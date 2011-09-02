/*
 * Copyright 2004-2005 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.codehaus.groovy.grails.web.pages;

import grails.util.Environment;
import grails.util.PluginBuildSettings;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.tools.ant.filters.StringInputStream;
import org.codehaus.groovy.grails.commons.ConfigurationHolder;
import org.codehaus.groovy.grails.plugins.GrailsPluginInfo;
import org.codehaus.groovy.grails.plugins.GrailsPluginUtils;
import org.codehaus.groovy.grails.web.taglib.GrailsTagRegistry;
import org.codehaus.groovy.grails.web.taglib.GroovySyntaxTag;
import org.codehaus.groovy.grails.web.taglib.exceptions.GrailsTagException;
import org.codehaus.groovy.grails.web.util.StreamByteBuffer;
import org.codehaus.groovy.grails.web.util.StreamCharBuffer;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * NOTE: Based on work done by the GSP standalone project
 * (https://gsp.dev.java.net/)
 * <p/>
 * Parsing implementation for GSP files
 *
 * @author Troy Heninger
 * @author Graeme Rocher
 * @author Lari Hotari
 */
public class GspTagParser extends GroovyPageParser {

    public static final Log LOG = LogFactory.getLog(GspTagParser.class);

    private static final Pattern PARA_BREAK = Pattern.compile(
            "/p>\\s*<p[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern ROW_BREAK = Pattern.compile(
            "((/td>\\s*</tr>\\s*<)?tr[^>]*>\\s*<)?td[^>]*>",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PARSE_TAG_FIRST_PASS = Pattern.compile(
            "(\\s*(\\S+)\\s*=\\s*[\"]([^\"]*)[\"][\\s|>]{1}){1}");
    private static final Pattern PARSE_TAG_SECOND_PASS = Pattern.compile(
            "(\\s*(\\S+)\\s*=\\s*[']([^']*)['][\\s|>]{1}){1}");
    private static final Pattern PAGE_DIRECTIVE_PATTERN = Pattern.compile(
            "(\\w+)\\s*=\\s*\"([^\"]*)\"");
    private static final Pattern TAG_COMMENT__PATTERN = Pattern.compile("<%--(/\\*\\*.*?\\*/)--%>", Pattern.DOTALL);

    private GroovyPageScanner_ scan;
    private GSPWriter out;
    private String tagName;
    private String className;
    private String packageName;
    private String sourceName; // last segment of the file name (eg- index.gsp)
    private boolean finalPass = false;
    private int tagIndex;
    private Map<Object, Object> tagContext;
    private Stack<TagMeta> tagMetaStack = new Stack<TagMeta>();
    private GrailsTagRegistry tagRegistry = GrailsTagRegistry.getInstance();
    private Environment environment;
    private List<String> htmlParts = new ArrayList<String>();

//not needed    private static SitemeshPreprocessor sitemeshPreprocessor = new SitemeshPreprocessor();

    Set<Integer> bodyVarsDefined = new HashSet<Integer>();
    Map<Integer, String> attrsVarsMapDefinition = new HashMap<Integer, String>();

    int closureLevel = 0;

    /*
     * Set to true when whitespace is currently being saved for later output if
     * the next tag isn't set to swallow it
     */
    private boolean currentlyBufferingWhitespace;

    /*
     * Set to true if the last output was not whitespace, so that we can detect
     * when a tag has illegal content before it
     */
    private boolean previousContentWasNonWhitespace;

    private StringBuffer whitespaceBuffer = new StringBuffer();

    private String contentType = DEFAULT_CONTENT_TYPE;
    private boolean doNextScan = true;
    private int state;
    private static final String DEFAULT_CONTENT_TYPE = "text/html;charset=UTF-8";
    private int constantCount = 0;
    private Map<String, Integer> constantsToNumbers = new HashMap<String, Integer>();

    private final String pageName;
    public static final String[] DEFAULT_IMPORTS = {
            "org.codehaus.groovy.grails.web.taglib.*"
    };
    public static final String PROLOG = "// Generated code, DO NOT EDIT!";
    private static final String CONFIG_PROPERTY_GSP_ENCODING = "grails.views.gsp.encoding";
    private static final String CONFIG_PROPERTY_GSP_KEEPGENERATED_DIR = "grails.views.gsp.keepgenerateddir";
// not needed      private static final String CONFIG_PROPERTY_GSP_SITEMESH_PREPROCESS = "grails.views.gsp.sitemesh.preprocess";

    private static final String IMPORT_DIRECTIVE = "import";
    private static final String CONTENT_TYPE_DIRECTIVE = "contentType";
    private static final String DEFAULT_CODEC_DIRECTIVE = "defaultCodec";
    private static final String NAMESPACE_DIRECTIVE = "namespace";
    // not needed   private static final String SITEMESH_PREPROCESS_DIRECTIVE = "sitemeshPreprocess";
    private static final String PAGE_DIRECTIVE = "page";

    private static final String TAGLIB_DIRECTIVE = "taglib";
    private String gspEncoding;
    private String pluginAnnotation;
    public static final String GROOVY_SOURCE_CHAR_ENCODING = "UTF-8";
    private Map<String, String> jspTags = new HashMap<String, String>();
    private long lastModified;
    private boolean precompileMode;
    // not needed   private boolean sitemeshPreprocessMode=false;
    private PluginBuildSettings pluginBuildSettings = GrailsPluginUtils.getPluginBuildSettings();
    private String defaultCodecDirectiveValue;
    private String tagNamespace;
    private String tagComment;
    private List<String> requiredAttrs = new ArrayList<String>();

    public String getContentType() {
        return contentType;
    }

    public int getCurrentOutputLineNumber() {
        return scan.getLineNumberForToken();
    }

    public Map<String, String> getJspTags() {
        return jspTags;
    }

    class TagMeta {
        String name;
        String namespace;
        Object instance;
        boolean isDynamic;
        boolean hasAttributes;
        int lineNumber;
        boolean emptyTag;
        @SuppressWarnings("hiding")
        int tagIndex;
        boolean bufferMode = false;
        int bufferPartNumber = -1;

        @Override
        public String toString() {
            return "<" + namespace + ":" + name + ">";
        }
    }

    @SuppressWarnings("rawtypes")
    public GspTagParser(GspTagInfo tagInfo) throws IOException {
        super(tagInfo.getTagName(),
                tagInfo.getTagName(),
                tagInfo.getTagLibFileName(),
                new StringInputStream(tagInfo.getText()));
        className = tagInfo.getTagLibName();
        pageName = tagInfo.getTagLibName();
        sourceName = tagInfo.getTagLibName() + ".gsp";
        packageName = tagInfo.getPackageName();
        tagName = tagInfo.getTagName();
        Map config = ConfigurationHolder.getFlatConfig();
        GrailsPluginInfo info = pluginBuildSettings.getPluginInfoForSource(tagInfo.getFilePath());
        if (info != null) {
            pluginAnnotation = "@GrailsPlugin(name='" + info.getName() + "', version='" +
                    info.getVersion() + "')";
        }

        // Get the GSP file encoding from Config, or fall back to system
        // file.encoding if none set
        Object gspEnc = config.get(CONFIG_PROPERTY_GSP_ENCODING);
        if ((gspEnc != null) && (gspEnc.toString().trim().length() > 0)) {
            gspEncoding = gspEnc.toString();
        } else {
            gspEncoding = System.getProperty("file.encoding", "us-ascii");
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("GSP file encoding set to: " + gspEncoding);
        }

        String gspSource = tagInfo.getText();
        scan = new GroovyPageScanner_(gspSource);
        environment = Environment.getCurrent();
        Matcher m = TAG_COMMENT__PATTERN.matcher(gspSource);
        if (m.find()) {
            tagComment = m.group(1);
        }
    }

    public int[] getLineNumberMatrix() {
        return out.getLineNumbers();
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public InputStream parse() {
        File keepGeneratedDirectory = resolveKeepGeneratedDirectory();

        StreamCharBuffer streamBuffer = new StreamCharBuffer(1024);
        StreamByteBuffer byteOutputBuffer = new StreamByteBuffer(1024,
                StreamByteBuffer.ReadMode.RETAIN_AFTER_READING);

        try {
            streamBuffer.connectTo(new OutputStreamWriter(byteOutputBuffer.getOutputStream(),
                    GROOVY_SOURCE_CHAR_ENCODING), true);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("Grails cannot run unless your environment supports UTF-8!");
        }

        File keepGeneratedFile = null;
        Writer keepGeneratedWriter = null;
        if (keepGeneratedDirectory != null) {
            keepGeneratedFile = new File(keepGeneratedDirectory, className);
            try {
                keepGeneratedWriter = new OutputStreamWriter(
                        new FileOutputStream(keepGeneratedFile),
                        GROOVY_SOURCE_CHAR_ENCODING);
            } catch (IOException e) {
                LOG.warn("Cannot open keepgenerated file for writing. File's absolute path is '" +
                        keepGeneratedFile.getAbsolutePath() + "'");
                keepGeneratedFile = null;
            }
            streamBuffer.connectTo(keepGeneratedWriter, true);
        }

        Writer target = streamBuffer.getWriter();
        try {
            generateGsp(target, false);

            if (LOG.isDebugEnabled()) {
                if (keepGeneratedFile != null) {
                    LOG.debug("Compiled GSP into Groovy code. Source is in " + keepGeneratedFile);
                } else {
                    LOG.debug("Configure " + CONFIG_PROPERTY_GSP_KEEPGENERATED_DIR +
                            " property to view generated source.");
                }
            }
            return byteOutputBuffer.getInputStream();
        } finally {
            IOUtils.closeQuietly(keepGeneratedWriter);
        }
    }

    private File resolveKeepGeneratedDirectory() {
        File keepGeneratedDirectory = null;

        Object keepDirObj = ConfigurationHolder.getFlatConfig().get(CONFIG_PROPERTY_GSP_KEEPGENERATED_DIR);
        if (keepDirObj instanceof File) {
            keepGeneratedDirectory = ((File) keepDirObj);
        } else if (keepDirObj != null) {
            keepGeneratedDirectory = new File(String.valueOf(keepDirObj));
        }
        if (keepGeneratedDirectory != null && !keepGeneratedDirectory.isDirectory()) {
            LOG.warn("The directory specified with " + CONFIG_PROPERTY_GSP_KEEPGENERATED_DIR +
                    " config parameter doesn't exist or isn't a readable directory. Absolute path: '" +
                    keepGeneratedDirectory.getAbsolutePath() + "' Keepgenerated will be disabled.");
            keepGeneratedDirectory = null;
        }
        return keepGeneratedDirectory;
    }

    public void generateGsp(Writer target) {
        generateGsp(target, true);
    }

    public void generateGsp(Writer target, @SuppressWarnings("hiding") boolean precompileMode) {
        this.precompileMode = precompileMode;

        out = new GSPWriter(target, this);
        if (packageName != null && packageName.length() > 0) {
            out.println("package " + packageName);
        }
        out.println(PROLOG);
        out.println();
        page();
        finalPass = true;
        scan.reset();
        previousContentWasNonWhitespace = false;
        currentlyBufferingWhitespace = false;
        page();

        out.flush();
        scan = null;
    }

    public void writeHtmlParts(File filename) throws IOException {
        DataOutputStream dataOut = null;
        try {
            dataOut = new DataOutputStream(new BufferedOutputStream(
                    new FileOutputStream(filename)));
            dataOut.writeInt(htmlParts.size());
            for (String part : htmlParts) {
                dataOut.writeUTF(part);
            }
        } finally {
            IOUtils.closeQuietly(dataOut);
        }
    }

    public void writeLineNumbers(File filename) throws IOException {
        DataOutputStream dataOut = null;
        try {
            dataOut = new DataOutputStream(new BufferedOutputStream(
                    new FileOutputStream(filename)));
            int lineNumbersCount = out.getCurrentLineNumber() - 1;
            int[] lineNumbers = out.getLineNumbers();
            dataOut.writeInt(lineNumbersCount);
            for (int i = 0; i < lineNumbersCount; i++) {
                dataOut.writeInt(lineNumbers[i]);
            }
        } finally {
            IOUtils.closeQuietly(dataOut);
        }
    }

    private void declare(boolean gsp) {
        if (finalPass) {
            return;
        }

        LOG.debug("parse: declare");

        out.println();
        write(scan.getToken().trim(), gsp);
        out.println();
        out.println();
    }

    private void direct() {
        if (finalPass) {
            return;
        }

        LOG.debug("parse: direct");

        String text = scan.getToken();
        text = text.trim();
        if (text.startsWith(PAGE_DIRECTIVE)) {
            directPage(text);
        } else if (text.startsWith(TAGLIB_DIRECTIVE)) {
            directJspTagLib(text);
        }
    }

    private void directPage(String text) {

        text = text.trim();
        // LOG.debug("directPage(" + text + ')');
        Matcher mat = PAGE_DIRECTIVE_PATTERN.matcher(text);
        for (int ix = 0; ; ) {
            if (!mat.find(ix)) {
                return;
            }
            String name = mat.group(1);
            String value = mat.group(2);
            if (name.equals(IMPORT_DIRECTIVE)) {
                pageImport(value);
            }
            if (name.equals(NAMESPACE_DIRECTIVE)) {
                tagNamespace = value;
            }
            if (name.equals(CONTENT_TYPE_DIRECTIVE)) {
                contentType(value);
            }
            if (name.equals("required")) {
                requiredAttrs.add(value);
            }
            if (name.equals(DEFAULT_CODEC_DIRECTIVE)) {
                defaultCodecDirectiveValue = value.trim();
            }
            ix = mat.end();
        }
    }

    private void directJspTagLib(String text) {

        text = text.substring(TAGLIB_DIRECTIVE.length() + 1, text.length());
        Map<String, String> attrs = new LinkedHashMap<String, String>();
        populateMapWithAttributes(attrs, text + '>');

        String prefix = attrs.get("\"prefix\"");
        String uri = attrs.get("\"uri\"");

        if (uri != null && prefix != null) {

            final String namespace = prefix.substring(1, prefix.length() - 1);
            if (!GroovyPage.DEFAULT_NAMESPACE.equals(namespace)) {
                jspTags.put(namespace, uri.substring(1, uri.length() - 1));
            } else {
                LOG.error("You cannot override the default 'g' namespace with the directive <%@ taglib prefix=\"g\" %>. Please select another namespace.");
            }
        }
    }

    private void contentType(String value) {
        contentType = value;
    }

    private void scriptletExpr() {
        if (!finalPass) {
            return;
        }

        LOG.debug("parse: expr");

        String text = scan.getToken().trim();
        out.printlnToResponse(text);
    }

    private void expr() {
        if (!finalPass) return;

        LOG.debug("parse: expr");

        String text = scan.getToken().trim();
        text = getExpressionText(text);
        if (text != null && text.length() > 2 && text.startsWith("(") && text.endsWith(")")) {
            out.printlnToResponse("out", text.substring(1, text.length() - 1));
        } else {
            out.printlnToResponse("out", text);
        }
    }

    /**
     * Returns an expression text for the given expression
     *
     * @param text The text
     * @return An expression text
     */
    public String getExpressionText(String text) {
        boolean safeDereference = false;
        if (text.endsWith("?")) {
            text = text.substring(0, text.length() - 1);
            safeDereference = true;
        }

        //TODO: dev mode, use evaluate
        // add extra parenthesis, see http://jira.codehaus.org/browse/GRAILS-4351
        // or GroovyPagesTemplateEngineTests.testForEachInProductionMode

        text = "(" + text + ")" + (safeDereference ? "?" : "");
        return text;
    }

    private String escapeGroovy(String text) {
        return text.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n").replace("\r", "\\r");
    }

    /**
     * Write to the outputstream ONLY if the string is not blank, else we hold
     * it back in case it is to be swallowed between tags
     */
    @SuppressWarnings("unused")
    private void bufferedPrintlnToResponse(String s) {
        if (currentlyBufferingWhitespace) {
            whitespaceBuffer.append(s);
        } else {
            flushTagBuffering();
            out.printlnToResponse(s);
        }
    }

    private void htmlPartPrintlnToResponse(int partNumber) {
        if (!tagMetaStack.isEmpty()) {
            TagMeta tm = tagMetaStack.peek();
            if (tm.bufferMode && tm.bufferPartNumber == -1) {
                tm.bufferPartNumber = partNumber;
                return;
            }
        }

        flushTagBuffering();

        htmlPartPrintlnRaw(partNumber);
    }

    private void htmlPartPrintlnRaw(int partNumber) {
        out.print("out.print('");
        out.print(escapeGroovy(htmlParts.get(partNumber)));
        out.print("')");
        out.println();
    }

    public void flushTagBuffering() {
        if (!tagMetaStack.isEmpty()) {
            TagMeta tm = tagMetaStack.peek();
            if (tm.bufferMode) {
                writeTagBodyStart(tm);
                if (tm.bufferPartNumber != -1) {
                    htmlPartPrintlnRaw(tm.bufferPartNumber);
                }
                tm.bufferMode = false;
            }
        }
    }

    private void html() {
        if (!finalPass) return;


        LOG.debug("parse: html");

        String text = scan.getToken();
        if (text.length() == 0) {
            return;
        }

        // If we detect it is all whitespace, we need to keep it for later
        // If it is not whitespace, we need to flush any whitespace we do have
        boolean contentIsWhitespace = !Pattern.compile("\\S").matcher(text).find();
        if (!contentIsWhitespace && currentlyBufferingWhitespace) {
            flushBufferedWhiteSpace();
        } else {
            currentlyBufferingWhitespace = contentIsWhitespace;
        }
        // We need to know if the last content output was not whitespace, for tag safety checks
        previousContentWasNonWhitespace = !contentIsWhitespace;

        if (currentlyBufferingWhitespace) {
            whitespaceBuffer.append(text);
        } else {
            appendHtmlPart(text);
        }
    }

    private void appendHtmlPart(String text) {
        // flush previous white space if any
        if (whitespaceBuffer.length() > 0) {
            if (text != null) {
                whitespaceBuffer.append(text);
            }
            text = whitespaceBuffer.toString();
            clearBufferedWhiteSpace();
        }

        // de-dupe constants
        Integer constantNumber = constantsToNumbers.get(text);
        if (constantNumber == null) {
            constantNumber = new Integer(constantCount++);
            constantsToNumbers.put(text, constantNumber);
            htmlParts.add(text);
        }
        htmlPartPrintlnToResponse(constantNumber);
    }

    private static boolean match(CharSequence pat, CharSequence text, int start) {
        int ix = start, ixz = text.length(), ixy = start + pat.length();
        if (ixz > ixy) {
            ixz = ixy;
        }
        if (pat.length() > ixz - start) {
            return false;
        }

        for (; ix < ixz; ix++) {
            if (Character.toLowerCase(text.charAt(ix)) != Character.toLowerCase(pat.charAt(ix - start))) {
                return false;
            }
        }
        return true;
    }

    private static int match(Pattern pat, CharSequence text, int start) {
        Matcher mat = pat.matcher(text);
        if (mat.find(start) && mat.start() == start) {
            return mat.end();
        }
        return 0;
    }

    private void page() {

        LOG.debug("parse: page");

        if (finalPass) {
            out.println();
            if (pluginAnnotation != null) {
                out.println(pluginAnnotation);
            }
            out.print("class ");
            out.print(className);
            out.println(" {");
            if (tagNamespace != null) {
                out.print("static namespace = \"");
                out.print(tagNamespace);
                out.println('"');
            }
            if (tagComment != null) {
                out.println(tagComment);
            }
            out.println("def " + tagName + " = { attrs, body ->");

            for (String required : requiredAttrs) {
                String requiredAttributes[] = required.trim().split("\\s*,\\s*");
                for (String attribute : requiredAttributes) {
                    out.println("assert attrs." + attribute + ", \"missing required attribute " + attribute + "\"");
                }
            }
        }

        loop:
        for (; ; ) {
            if (doNextScan) {
                state = scan.nextToken();
            } else {
                doNextScan = true;
            }

            // Flush any buffered whitespace if there's not a possibility of more whitespace
            // or a new tag which will handle flushing as necessary
            if ((state != GSTART_TAG) && (state != HTML)) {
                flushBufferedWhiteSpace();
                previousContentWasNonWhitespace = false; // well, we don't know
            }

            switch (state) {
                case EOF:
                    break loop;
                case HTML:
                    html();
                    break;
                case JEXPR:
                    scriptletExpr();
                    break;
                case JSCRIPT:
                    script(false);
                    break;
                case JDIRECT:
                    direct();
                    break;
                case JDECLAR:
                    declare(false);
                    break;
                case GEXPR:
                    expr();
                    break;
                case GSCRIPT:
                    script(true);
                    break;
                case GDIRECT:
                    direct();
                    break;
                case GDECLAR:
                    declare(true);
                    break;
                case GSTART_TAG:
                    startTag();
                    break;
                case GEND_EMPTY_TAG:
                case GEND_TAG:
                    endTag();
                    break;
            }
        }

        if (finalPass) {
            if (!tagMetaStack.isEmpty()) {
                throw new GrailsTagException("Grails tags were not closed! [" +
                        tagMetaStack + "] in GSP " + pageName + "", pageName,
                        out.getCurrentLineNumber());
            }

            out.println("}");

            if (shouldAddLineNumbers()) {
                addLineNumbers();
            }

            out.println("}");
        } else {
            for (int i = 0; i < DEFAULT_IMPORTS.length; i++) {
                out.print("import ");
                out.println(DEFAULT_IMPORTS[i]);
            }
        }
    }

    /**
     * Determines if the line numbers array should be added to the generated Groovy class.
     *
     * @return true if they should
     */
    private boolean shouldAddLineNumbers() {
        try {
            // for now, we support this through a system property.
            String prop = System.getenv("GROOVY_PAGE_ADD_LINE_NUMBERS");
            return Boolean.valueOf(prop).booleanValue();
        } catch (Exception e) {
            // something wild happened
            return false;
        }
    }

    /**
     * Adds the line numbers array to the end of the generated Groovy ModuleNode
     * in a way suitable for the LineNumberTransform AST transform to operate on it
     */
    private void addLineNumbers() {
        out.println();
        out.println("@org.codehaus.groovy.grails.web.transform.LineNumber(");
        out.print("\tlines = [");
        // get the line numbers here.  this will mean that the last 2 lines will not be captured in the
        // line number information, but that's OK since a user cannot set a breakpoint there anyway.
        int[] lineNumbers = filterTrailing0s(out.getLineNumbers());

        for (int i = 0; i < lineNumbers.length; i++) {
            out.print(lineNumbers[i]);
            if (i < lineNumbers.length - 1) {
                out.print(", ");
            }
        }
        out.println("],");
        out.println("\tsourceName = \"" + sourceName + "\"");
        out.println(")");
        out.println("class ___LineNumberPlaceholder { }");
    }

    /**
     * Filters trailing 0s from the line number array
     *
     * @param lineNumbers the line number array
     * @return a new array that removes all 0s from the end of it
     */
    private int[] filterTrailing0s(int[] lineNumbers) {
        int startLocation = lineNumbers.length - 1;
        for (int i = lineNumbers.length - 1; i >= 0; i--) {
            if (lineNumbers[i] > 0) {
                startLocation = i + 1;
                break;
            }
        }

        int[] newLineNumbers = new int[startLocation];
        System.arraycopy(lineNumbers, 0, newLineNumbers, 0, startLocation);
        return newLineNumbers;
    }

    private void endTag() {
        if (!finalPass) return;

        String tagName = scan.getToken().trim();
        String ns = scan.getNamespace();

        if (tagMetaStack.isEmpty())
            throw new GrailsTagException(
                    "Found closing Grails tag with no opening [" + tagName + "]", pageName,
                    out.getCurrentLineNumber());

        TagMeta tm = tagMetaStack.pop();
        String lastInStack = tm.name;
        String lastNamespaceInStack = tm.namespace;

        // if the tag name is blank then it has been closed by the start tag ie <tag />
        if (StringUtils.isBlank(tagName)) {
            tagName = lastInStack;
        }

        if (!lastInStack.equals(tagName) || !lastNamespaceInStack.equals(ns)) {
            throw new GrailsTagException("Grails tag [" + lastNamespaceInStack +
                    ":" + lastInStack + "] was not closed", pageName, out.getCurrentLineNumber());
        }

        if (GroovyPage.DEFAULT_NAMESPACE.equals(ns) && tagRegistry.isSyntaxTag(tagName)) {
            if (tm.instance instanceof GroovySyntaxTag) {
                GroovySyntaxTag tag = (GroovySyntaxTag) tm.instance;
                tag.doEndTag();
            } else {
                throw new GrailsTagException("Grails tag [" + tagName +
                        "] was not closed", pageName,
                        out.getCurrentLineNumber());
            }
        } else {
            String bodyTagClosureName = "null";
            if (!tm.emptyTag && !tm.bufferMode) {
                bodyTagClosureName = "body" + tagIndex;
                out.println("})");
                closureLevel--;
            }

            if (tm.bufferMode && tm.bufferPartNumber != -1) {
                if (!bodyVarsDefined.contains(tm.tagIndex)) {
                    out.print("def ");
                    bodyVarsDefined.add(tm.tagIndex);
                }
                out.print("body" + tm.tagIndex + " = '" + escapeGroovy(htmlParts.get(tm.bufferPartNumber)));
                out.println("'");
                bodyTagClosureName = "body" + tm.tagIndex;
                tm.bufferMode = false;
            }

            if (jspTags.containsKey(ns)) {
                String uri = jspTags.get(ns);
                out.println("jspTag = tagLibraryResolver?.resolveTagLibrary('" +
                        uri + "')?.getTag('" + tagName + "')");
                out.println("if (!jspTag) throw new GrailsTagException('Unknown JSP tag " +
                        ns + ":" + tagName + "')");
                out.println("jspTag.doTag(out," + attrsVarsMapDefinition.get(tagIndex) + ", " +
                        bodyTagClosureName + ")");
            } else {
                if (tm.hasAttributes) {
                    out.println("out.print(" + ns + '.' + tagName + "(" + attrsVarsMapDefinition.get(tagIndex) +
                            "," + bodyTagClosureName + "))");
                } else {
                    out.println("out.print(" + ns + '.' + tagName + "(" + "[:]," + bodyTagClosureName + "))");
                }
            }
        }

        tm.bufferMode = false;
        tagIndex--;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void startTag() {
        if (!finalPass) return;

        tagIndex++;

        String text;
        StringBuilder buf = new StringBuilder(scan.getToken());
        String ns = scan.getNamespace();

        boolean emptyTag = false;

        state = scan.nextToken();
        while (state != HTML && state != GEND_TAG && state != GEND_EMPTY_TAG && state != EOF) {
            if (state == GTAG_EXPR) {
                buf.append("${");
                buf.append(scan.getToken().trim());
                buf.append("}");
            } else {
                buf.append(scan.getToken());
            }
            state = scan.nextToken();
        }
        if (state == GEND_EMPTY_TAG) {
            emptyTag = true;
        }

        doNextScan = false;

        text = buf.toString();

        String tagName;
        Map attrs = new LinkedHashMap();
        text = text.replaceAll("[\r\n\t]", " "); // this line added TODO query this

        if (text.indexOf(' ') > -1) { // ignores carriage returns and new lines
            int i = text.indexOf(' ');
            tagName = text.substring(0, i);
            String attrTokens = text.substring(i, text.length());
            attrTokens += '>'; // closing bracket marker
            populateMapWithAttributes(attrs, attrTokens);
        } else {
            tagName = text;
        }

        if (state == EOF) {
            throw new GrailsTagException(
                    "Unexpected end of file encountered parsing Tag [" + tagName + "] for " + className +
                            ". Are you missing a closing brace '}'?", pageName,
                    out.getCurrentLineNumber());
        }

        flushTagBuffering();

        TagMeta tm = new TagMeta();
        tm.name = tagName;
        tm.namespace = ns;
        tm.hasAttributes = !attrs.isEmpty();
        tm.lineNumber = getCurrentOutputLineNumber();
        tm.emptyTag = emptyTag;
        tm.tagIndex = tagIndex;
        tagMetaStack.push(tm);

        if (GroovyPage.DEFAULT_NAMESPACE.equals(ns) && tagRegistry.isSyntaxTag(tagName)) {
            if (tagContext == null) {
                tagContext = new HashMap<Object, Object>();
                tagContext.put(GroovyPage.OUT, out);
                tagContext.put(GspTagParser.class, this);
            }
            GroovySyntaxTag tag = (GroovySyntaxTag) tagRegistry.newTag(tagName);
            tag.init(tagContext);
            tag.setAttributes(attrs);

            if (tag.isKeepPrecedingWhiteSpace() && currentlyBufferingWhitespace) {
                flushBufferedWhiteSpace();
            } else if (!tag.isAllowPrecedingContent() && previousContentWasNonWhitespace) {
                throw new GrailsTagException("Tag [" + tag.getName() +
                        "] cannot have non-whitespace characters directly preceding it.", pageName,
                        out.getCurrentLineNumber());
            } else {
                // If tag does not specify buffering of WS, we swallow it here
                clearBufferedWhiteSpace();
            }

            tag.doStartTag();
            tm.instance = tag;
        } else {
            // Custom taglibs have to always flush the whitespace, there's no
            // "allowPrecedingWhitespace" property on tags yet
            flushBufferedWhiteSpace();

            if (attrs.size() > 0) {
                FastStringWriter buffer = new FastStringWriter();
                buffer.print('[');
                for (Iterator<?> i = attrs.keySet().iterator(); i.hasNext(); ) {
                    String name = (String) i.next();
                    String cleanedName = name;
                    if (name.startsWith("\"") && name.endsWith("\"")) {
                        cleanedName = "'" + name.substring(1, name.length() - 1) + "'";
                    }
                    buffer.print(cleanedName);
                    buffer.print(':');

                    buffer.print(getExpressionText(attrs.get(name).toString()));
                    if (i.hasNext()) {
                        buffer.print(',');
                    } else {
                        buffer.print("] as GroovyPageAttributes");
                    }
                }
                attrsVarsMapDefinition.put(tagIndex, buffer.toString());
            }

            if (!emptyTag) {
                tm.bufferMode = true;
            }
        }
    }

    private void writeTagBodyStart(TagMeta tm) {
        if (tm.bufferMode) {
            tm.bufferMode = false;
            if (!bodyVarsDefined.contains(tm.tagIndex)) {
                out.print("def ");
                bodyVarsDefined.add(tm.tagIndex);
            }
            out.println("body" + tm.tagIndex + " = new GroovyPageTagBody(this,webRequest, {");
            closureLevel++;
        }
    }

    private void clearBufferedWhiteSpace() {
        whitespaceBuffer.delete(0, whitespaceBuffer.length());
        currentlyBufferingWhitespace = false;
    }

    // Write out any whitespace we saved between tags
    private void flushBufferedWhiteSpace() {
        if (currentlyBufferingWhitespace) {
            appendHtmlPart(null);
        }
        currentlyBufferingWhitespace = false;
    }

    private void populateMapWithAttributes(Map<String, String> attrs, String attrTokens) {
        // do first pass parse which retrieves double quoted attributes
        Matcher m = PARSE_TAG_FIRST_PASS.matcher(attrTokens);
        populateAttributesFromMatcher(m, attrs);

        // do second pass parse which retrieves single quoted attributes
        m = PARSE_TAG_SECOND_PASS.matcher(attrTokens);
        populateAttributesFromMatcher(m, attrs);
    }

    private void populateAttributesFromMatcher(Matcher m, Map<String, String> attrs) {
        while (m.find()) {
            String name = m.group(2);
            String val = m.group(3);
            name = '\"' + name + '\"';
            if (val.startsWith("${") && val.endsWith("}") && val.indexOf("${", 2) == -1) {
                val = val.substring(2, val.length() - 1);
            } else if (!(val.startsWith("[") && val.endsWith("]"))) {
                val = '\"' + val + '\"';
            }
            attrs.put(name, val);
        }
    }

    private void pageImport(String value) {
        // LOG.debug("pageImport(" + value + ')');
        String[] imports = Pattern.compile(";").split(value.subSequence(0, value.length()));
        for (int ix = 0; ix < imports.length; ix++) {
            out.print("import ");
            out.print(imports[ix]);
            out.println();
        }
    }

    private void script(boolean gsp) {
        flushTagBuffering();
        if (!finalPass) return;

        LOG.debug("parse: script");

        out.println();
        write(scan.getToken().trim(), gsp);
        out.println();
        out.println();
    }

    private void write(CharSequence text, boolean gsp) {
        if (!gsp) {
            out.print(text);
            return;
        }

        for (int ix = 0, ixz = text.length(); ix < ixz; ix++) {
            char c = text.charAt(ix);
            String rep = null;
            if (Character.isWhitespace(c)) {
                for (ix++; ix < ixz; ix++) {
                    if (Character.isWhitespace(text.charAt(ix))) {
                        continue;
                    }
                    ix--;
                    rep = " ";
                    break;
                }
            } else if (c == '&') {
                if (match("&semi;", text, ix)) {
                    rep = ";";
                    ix += 5;
                } else if (match("&amp;", text, ix)) {
                    rep = "&";
                    ix += 4;
                } else if (match("&lt;", text, ix)) {
                    rep = "<";
                    ix += 3;
                } else if (match("&gt;", text, ix)) {
                    rep = ">";
                    ix += 3;
                }
            } else if (c == '<') {
                if (match("<br>", text, ix) || match("<hr>", text, ix)) {
                    rep = "\n";
                    //incrementLineNumber();
                    ix += 3;
                } else {
                    int end = match(PARA_BREAK, text, ix);
                    if (end <= 0)
                        end = match(ROW_BREAK, text, ix);
                    if (end > 0) {
                        rep = "\n";
                        //incrementLineNumber();
                        ix = end;
                    }
                }
            }
            if (rep != null) {
                out.print(rep);
            } else {
                out.print(c);
            }
        }
    }

    public long getLastModified() {
        return lastModified;
    }

    public void setLastModified(long lastModified) {
        this.lastModified = lastModified;
    }

    public List<String> getHtmlParts() {
        return htmlParts;
    }

    public String[] getHtmlPartsArray() {
        return htmlParts.toArray(new String[htmlParts.size()]);
    }

    public boolean isInClosure() {
        return closureLevel > 0;
    }

    public String getDefaultCodecDirectiveValue() {
        return defaultCodecDirectiveValue;
    }
}
