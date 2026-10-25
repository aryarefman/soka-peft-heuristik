import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.*;

public class TransformDocxToPoppins {

    public static void main(String[] args) throws Exception {
        // Pemakaian: java tools/TransformDocxToPoppins.java docs/Tugas_3_Kelompok_8_Proposal.docx
        File inputFile = new File(args.length > 0 ? args[0] : "docs/Tugas_3_Kelompok_8_Proposal.docx");
        File tempOutputFile = new File(inputFile.getPath().replace(".docx", ".tmp.docx"));

        System.out.println("Processing docx: " + inputFile.getAbsolutePath());

        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipFile zf = new ZipFile(inputFile)) {
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry entry = en.nextElement();
                try (InputStream is = zf.getInputStream(entry)) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = is.read(buffer)) > 0) {
                        baos.write(buffer, 0, len);
                    }
                    entries.put(entry.getName(), baos.toByteArray());
                }
            }
        }

        // 1. Transform fontTable.xml
        if (entries.containsKey("word/fontTable.xml")) {
            byte[] bytes = entries.get("word/fontTable.xml");
            String xml = new String(bytes, StandardCharsets.UTF_8);
            if (!xml.contains("w:name=\"Poppins\"")) {
                String poppinsFont = "  <w:font w:name=\"Poppins\">\n" +
                        "    <w:panose1 w:val=\"020B0604020202020204\"/>\n" +
                        "    <w:charset w:val=\"00\"/>\n" +
                        "    <w:family w:val=\"swiss\"/>\n" +
                        "    <w:pitch w:val=\"variable\"/>\n" +
                        "  </w:font>\n";
                int insertPos = xml.lastIndexOf("</w:fonts>");
                if (insertPos != -1) {
                    xml = xml.substring(0, insertPos) + poppinsFont + xml.substring(insertPos);
                }
            }
            entries.put("word/fontTable.xml", xml.getBytes(StandardCharsets.UTF_8));
            System.out.println("Updated word/fontTable.xml with Poppins definition.");
        }

        // 2. Transform styles.xml
        if (entries.containsKey("word/styles.xml")) {
            byte[] bytes = entries.get("word/styles.xml");
            String xml = new String(bytes, StandardCharsets.UTF_8);

            // Replace font references
            xml = xml.replace("Arial Black", "Poppins");
            xml = xml.replace("Lucida Sans Unicode", "Poppins");

            // Update docDefaults
            if (xml.contains("<w:docDefaults>")) {
                int defStart = xml.indexOf("<w:docDefaults>");
                int defEnd = xml.indexOf("</w:docDefaults>") + "</w:docDefaults>".length();
                String newDefaults = "<w:docDefaults>\n" +
                        "    <w:rPrDefault>\n" +
                        "      <w:rPr>\n" +
                        "        <w:rFonts w:ascii=\"Poppins\" w:hAnsi=\"Poppins\" w:eastAsia=\"Poppins\" w:cs=\"Poppins\"/>\n" +
                        "        <w:sz w:val=\"22\"/>\n" +
                        "        <w:szCs w:val=\"22\"/>\n" +
                        "        <w:color w:val=\"111827\"/>\n" +
                        "        <w:lang w:val=\"id-ID\"/>\n" +
                        "      </w:rPr>\n" +
                        "    </w:rPrDefault>\n" +
                        "    <w:pPrDefault>\n" +
                        "      <w:pPr>\n" +
                        "        <w:spacing w:line=\"276\" w:lineRule=\"auto\" w:after=\"120\"/>\n" +
                        "        <w:jc w:val=\"both\"/>\n" +
                        "      </w:pPr>\n" +
                        "    </w:pPrDefault>\n" +
                        "  </w:docDefaults>";
                xml = xml.substring(0, defStart) + newDefaults + xml.substring(defEnd);
            }

            entries.put("word/styles.xml", xml.getBytes(StandardCharsets.UTF_8));
            System.out.println("Updated word/styles.xml with Poppins styles and natural line spacing.");
        }

        // 3. Transform document.xml using DOM
        if (entries.containsKey("word/document.xml")) {
            byte[] bytes = entries.get("word/document.xml");
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(new ByteArrayInputStream(bytes));

            transformDocumentXml(doc);

            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.STANDALONE, "yes");
            transformer.setOutputProperty(OutputKeys.INDENT, "no");

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            transformer.transform(new DOMSource(doc), new StreamResult(baos));
            entries.put("word/document.xml", baos.toByteArray());
            System.out.println("Transformed word/document.xml: removed w:w, removed w:spacing, merged runs, set Poppins.");
        }

        // Write output zip
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(tempOutputFile))) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                ZipEntry ze = new ZipEntry(e.getKey());
                zos.putNextEntry(ze);
                zos.write(e.getValue());
                zos.closeEntry();
            }
        }

        // Replace original final file
        if (inputFile.delete()) {
            tempOutputFile.renameTo(inputFile);
            System.out.println("Saved transformed document to: " + inputFile.getAbsolutePath());
        } else {
            System.out.println("Could not replace directly, temp file is at: " + tempOutputFile.getAbsolutePath());
        }
    }

    private static void transformDocumentXml(Document doc) {
        // 1. Process all <w:rPr> elements
        NodeList rPrList = doc.getElementsByTagNameNS("*", "rPr");
        for (int i = 0; i < rPrList.getLength(); i++) {
            Element rPr = (Element) rPrList.item(i);

            // Remove all <w:w> (character horizontal scale)
            List<Element> toRemove = new ArrayList<>();
            NodeList wList = rPr.getElementsByTagNameNS("*", "w");
            for (int j = 0; j < wList.getLength(); j++) {
                toRemove.add((Element) wList.item(j));
            }

            // Remove all <w:spacing> (character tracking/spacing) within <w:rPr>
            NodeList spList = rPr.getElementsByTagNameNS("*", "spacing");
            for (int j = 0; j < spList.getLength(); j++) {
                toRemove.add((Element) spList.item(j));
            }

            for (Element el : toRemove) {
                rPr.removeChild(el);
            }

            // Replace font in <w:rFonts>
            NodeList rfList = rPr.getElementsByTagNameNS("*", "rFonts");
            for (int j = 0; j < rfList.getLength(); j++) {
                Element rf = (Element) rfList.item(j);
                String[] attrs = {"w:ascii", "w:hAnsi", "w:cs", "w:eastAsia"};
                for (String attr : attrs) {
                    if (rf.hasAttribute(attr)) {
                        String val = rf.getAttribute(attr);
                        if ("Arial Black".equals(val) || "Lucida Sans Unicode".equals(val) || "Times New Roman".equals(val)) {
                            rf.setAttribute(attr, "Poppins");
                        }
                    }
                }
            }
        }

        // 2. Process all <w:pPr> elements
        NodeList pPrList = doc.getElementsByTagNameNS("*", "pPr");
        for (int i = 0; i < pPrList.getLength(); i++) {
            Element pPr = (Element) pPrList.item(i);

            // Check <w:ind> for artificial PDF margins (w:right="359", etc.)
            NodeList indList = pPr.getElementsByTagNameNS("*", "ind");
            for (int j = 0; j < indList.getLength(); j++) {
                Element ind = (Element) indList.item(j);
                if (ind.hasAttribute("w:right")) {
                    String rVal = ind.getAttribute("w:right");
                    // If right margin is around 358-360 (0.25 inch PDF crop artifact), remove it
                    try {
                        int r = Integer.parseInt(rVal);
                        if (r >= 350 && r <= 375) {
                            ind.removeAttribute("w:right");
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }

            // Improve tight line spacing in paragraphs (w:line="240" or "268" -> 276 = 1.15x)
            NodeList spList = pPr.getElementsByTagNameNS("*", "spacing");
            for (int j = 0; j < spList.getLength(); j++) {
                Element sp = (Element) spList.item(j);
                if (sp.hasAttribute("w:line")) {
                    String lineVal = sp.getAttribute("w:line");
                    try {
                        int l = Integer.parseInt(lineVal);
                        if (l > 0 && l < 276) {
                            sp.setAttribute("w:line", "276");
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }
        }

        // 3. Process all paragraphs: merge consecutive text runs with identical formatting
        NodeList pList = doc.getElementsByTagNameNS("*", "p");
        for (int i = 0; i < pList.getLength(); i++) {
            Element p = (Element) pList.item(i);
            mergeParagraphRuns(doc, p);
        }
    }

    private static void mergeParagraphRuns(Document doc, Element p) {
        Node child = p.getFirstChild();
        Element prevRun = null;

        while (child != null) {
            Node nextChild = child.getNextSibling();
            if (child.getNodeType() == Node.ELEMENT_NODE && "r".equals(child.getLocalName())) {
                Element currRun = (Element) child;

                // Check if this run only contains a <w:t> text element (no drawing, no br, no tab)
                Element currT = getSingleTextChild(currRun);
                if (currT != null && prevRun != null) {
                    Element prevT = getSingleTextChild(prevRun);
                    if (prevT != null && areRunPrCompatible(prevRun, currRun)) {
                        // Merge currT text into prevT
                        String combined = prevT.getTextContent() + currT.getTextContent();
                        prevT.setTextContent(combined);
                        // Ensure xml:space="preserve"
                        if (combined.startsWith(" ") || combined.endsWith(" ") || combined.contains("  ")) {
                            prevT.setAttribute("xml:space", "preserve");
                        }
                        // Remove currRun from paragraph
                        p.removeChild(currRun);
                        child = nextChild;
                        continue;
                    }
                }

                if (currT != null) {
                    String text = currT.getTextContent();
                    if (text.startsWith(" ") || text.endsWith(" ")) {
                        currT.setAttribute("xml:space", "preserve");
                    }
                }

                prevRun = currRun;
            } else if (child.getNodeType() == Node.ELEMENT_NODE && "pPr".equals(child.getLocalName())) {
                // pPr, skip
            } else {
                // Non-run element (e.g. hyperlink, bookmark, etc.), reset prevRun
                prevRun = null;
            }
            child = nextChild;
        }
    }

    private static Element getSingleTextChild(Element run) {
        Element foundT = null;
        NodeList children = run.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE) {
                String name = n.getLocalName();
                if ("rPr".equals(name)) {
                    continue;
                }
                if ("t".equals(name)) {
                    if (foundT == null) {
                        foundT = (Element) n;
                    } else {
                        return null; // More than 1 text child
                    }
                } else {
                    return null; // Contains drawing, br, tab, etc.
                }
            }
        }
        return foundT;
    }

    private static boolean areRunPrCompatible(Element r1, Element r2) {
        Element rPr1 = getChildElement(r1, "rPr");
        Element rPr2 = getChildElement(r2, "rPr");
        if (rPr1 == null && rPr2 == null) return true;
        if (rPr1 == null || rPr2 == null) return false;

        // Compare key attributes: bold, italic, size, color
        boolean b1 = isFlagSet(rPr1, "b");
        boolean b2 = isFlagSet(rPr2, "b");
        if (b1 != b2) return false;

        boolean i1 = isFlagSet(rPr1, "i");
        boolean i2 = isFlagSet(rPr2, "i");
        if (i1 != i2) return false;

        String sz1 = getAttrVal(rPr1, "sz", "w:val");
        String sz2 = getAttrVal(rPr2, "sz", "w:val");
        if (!Objects.equals(sz1, sz2)) return false;

        String col1 = getAttrVal(rPr1, "color", "w:val");
        String col2 = getAttrVal(rPr2, "color", "w:val");
        if (!Objects.equals(col1, col2)) return false;

        return true;
    }

    private static boolean isFlagSet(Element parent, String tag) {
        Element el = getChildElement(parent, tag);
        if (el == null) return false;
        if (el.hasAttribute("w:val")) {
            String v = el.getAttribute("w:val");
            return !"0".equals(v) && !"false".equals(v) && !"off".equals(v);
        }
        return true;
    }

    private static String getAttrVal(Element parent, String tag, String attr) {
        Element el = getChildElement(parent, tag);
        if (el == null) return null;
        return el.getAttribute(attr);
    }

    private static Element getChildElement(Element parent, String localName) {
        NodeList nl = parent.getChildNodes();
        for (int i = 0; i < nl.getLength(); i++) {
            Node n = nl.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE && localName.equals(n.getLocalName())) {
                return (Element) n;
            }
        }
        return null;
    }
}
