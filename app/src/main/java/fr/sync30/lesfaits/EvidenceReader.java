package fr.sync30.lesfaits;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Base64;
import android.util.Xml;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** A bounded, local preview. Page numbers exposed to the user start at one. */
final class EvidenceReader {
    static final int LIMIT = 12000;
    private static final int XML_LIMIT = 2 * 1024 * 1024;

    static JSONObject read(Context context, Store.Evidence item, int page) throws Exception {
        File source = FileVault.safeFile(context, item.path);
        if (!source.isFile()) throw new IOException("Fichier introuvable");
        if (page < 1) throw new IOException("Numéro de page invalide");
        JSONObject out = new JSONObject().put("ok", true).put("nom", item.name).put("mime", item.mime)
                .put("taille_octets", item.size).put("page", page);
        String name = item.name.toLowerCase(Locale.ROOT);
        if (item.mime.equals("application/pdf") || name.endsWith(".pdf")) {
            try (ParcelFileDescriptor fd = ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY);
                 PdfRenderer pdf = new PdfRenderer(fd)) {
                out.put("pages", pdf.getPageCount());
                if (page > pdf.getPageCount()) throw new IOException("Page absente");
                try (PdfRenderer.Page p = pdf.openPage(page - 1)) {
                    float scale = 1000f / Math.max(p.getWidth(), p.getHeight());
                    Bitmap image = Bitmap.createBitmap(Math.max(1, Math.round(p.getWidth()*scale)),
                            Math.max(1, Math.round(p.getHeight()*scale)), Bitmap.Config.ARGB_8888);
                    try {
                        image.eraseColor(Color.WHITE);
                        p.render(image, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                        putImage(out, image);
                    } finally { image.recycle(); }
                    if (Build.VERSION.SDK_INT >= 35) {
                        StringBuilder text = new StringBuilder();
                        for (android.graphics.pdf.content.PdfPageTextContent part : p.getTextContents()) {
                            text.append(part.getText()).append('\n');
                            if (text.length() > LIMIT) break;
                        }
                        putText(out, text.toString());
                    }
                }
            }
        } else if (item.mime.startsWith("image/")) {
            requireFirst(page);
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(source.getPath(), opts);
            opts.inSampleSize = 1;
            while (Math.max(opts.outWidth, opts.outHeight) / opts.inSampleSize > 1200) opts.inSampleSize *= 2;
            opts.inJustDecodeBounds = false;
            Bitmap image = BitmapFactory.decodeFile(source.getPath(), opts);
            if (image == null) out.put("info", "Image à ouvrir dans un lecteur compatible.");
            else try { putImage(out, image); } finally { image.recycle(); }
        } else if (name.matches(".*\\.(docx|xlsx|pptx|odt|ods|odp)$")) {
            try (ZipFile zip = new ZipFile(source)) {
                String path;
                if (name.endsWith(".pptx") || name.endsWith(".xlsx")) {
                    boolean slides = name.endsWith(".pptx");
                    String prefix = slides ? "ppt/slides/slide" : "xl/worksheets/sheet";
                    List<String> parts = new ArrayList<>();
                    Enumeration<? extends ZipEntry> entries = zip.entries();
                    if (zip.size() > 20000) throw new IOException("Document trop complexe pour l’aperçu");
                    while (entries.hasMoreElements()) {
                        String part = entries.nextElement().getName();
                        if (part.matches(prefix + "[0-9]{1,8}\\.xml")) parts.add(part);
                    }
                    parts.sort(java.util.Comparator.comparingInt(s -> Integer.parseInt(s.substring(prefix.length(), s.length()-4))));
                    if (page > parts.size()) throw new IOException("Feuille ou diapositive absente");
                    out.put("pages", parts.size());
                    path = parts.get(page-1);
                } else {
                    requireFirst(page);
                    path = name.endsWith(".docx") ? "word/document.xml" : "content.xml";
                }
                List<String> shared = new ArrayList<>();
                if (name.endsWith(".xlsx") && zip.getEntry("xl/sharedStrings.xml") != null)
                    xmlText(zip, "xl/sharedStrings.xml", shared, true);
                putText(out, xmlText(zip, path, shared, false));
            }
        } else if (item.mime.startsWith("text/") || name.matches(".*\\.(txt|md|csv|tsv|json|xml|yaml|yml|log|ics|html)$")) {
            requireFirst(page);
            try (Reader in = new InputStreamReader(new FileInputStream(source), StandardCharsets.UTF_8)) {
                char[] buf = new char[LIMIT+1];
                int len = 0, n;
                while (len < buf.length && (n=in.read(buf,len,buf.length-len)) > 0) len += n;
                putText(out, new String(buf,0,len));
            }
        } else {
            requireFirst(page);
            out.put("info", "Fichier conservé. Ouvre-le dans une application compatible pour consulter son contenu.");
        }
        return out;
    }

    private static void requireFirst(int page) throws IOException {
        if (page != 1) throw new IOException("Ce format propose un seul aperçu");
    }

    private static void putText(JSONObject out, String text) throws Exception {
        out.put("texte", text.substring(0, Math.min(LIMIT,text.length())).trim());
        out.put("extrait_limite", text.length() > LIMIT);
    }

    static void putImage(JSONObject result, Bitmap bitmap) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 75, bytes);
        if (bytes.size() > 240000) {
            bytes.reset();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 38, bytes);
        }
        if (bytes.size() > 240000) throw new IOException("Aperçu trop volumineux");
        result.put("image_jpeg_base64", Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP));
    }

    private static String xmlText(ZipFile zip, String path, List<String> shared, boolean dictionary) throws Exception {
        ZipEntry entry = zip.getEntry(path);
        if (entry == null || entry.getSize() < 0 || entry.getSize() > XML_LIMIT)
            throw new IOException("Contenu absent ou trop volumineux pour l’aperçu");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (InputStream stream = zip.getInputStream(entry)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n=stream.read(buf)) != -1) {
                if (bytes.size()+n > XML_LIMIT) throw new IOException("Limite de lecture atteinte");
                bytes.write(buf,0,n);
            }
        }
        XmlPullParser xml = Xml.newPullParser();
        xml.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
        xml.setInput(new ByteArrayInputStream(bytes.toByteArray()), null);
        String parent="";
        boolean indexedCell = false, value = false;
        StringBuilder result = new StringBuilder(), cell = new StringBuilder();
        for (int event=xml.nextToken(); event!=XmlPullParser.END_DOCUMENT; event=xml.nextToken()) {
            String tag = xml.getName();
            if (event == XmlPullParser.DOCDECL) throw new IOException("Déclaration XML non prise en charge");
            if (event == XmlPullParser.START_TAG) {
                parent=tag;
                if ("c".equals(tag)) indexedCell="s".equals(xml.getAttributeValue(null,"t"));
                if ("v".equals(tag)) { value=true; cell.setLength(0); }
            } else if (event == XmlPullParser.TEXT || event == XmlPullParser.CDSECT) {
                if (value) cell.append(xml.getText());
                else if (!xml.isWhitespace() && !"f".equals(parent)) result.append(xml.getText());
            } else if (event == XmlPullParser.END_TAG) {
                parent="";
                if ("si".equals(tag) && dictionary) {
                    shared.add(result.toString()); result.setLength(0);
                } else if ("v".equals(tag)) {
                    value=false;
                    if (indexedCell) {
                        int index=Integer.parseInt(cell.toString().trim());
                        if (index>=0 && index<shared.size()) result.append(shared.get(index));
                    } else result.append(cell);
                } else if ("p".equals(tag)||"row".equals(tag)||"table-row".equals(tag)) result.append('\n');
                else if ("c".equals(tag)||"tc".equals(tag)||"table-cell".equals(tag)) result.append('\t');
            }
            if (!dictionary && result.length()>LIMIT) break;
        }
        return result.toString();
    }
}
