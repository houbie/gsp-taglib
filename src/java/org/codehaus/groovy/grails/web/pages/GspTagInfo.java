package org.codehaus.groovy.grails.web.pages;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class GspTagInfo {
    private String text;
    private String tagName;
    private String tagLibName;
    private String packageName;
    private String filePath;
    private String gspEncoding = "UTF-8";

    public GspTagInfo(String tagName, String packageName, String text) {
        this.tagName = tagName;
        this.packageName = packageName;
        tagLibName = "_" + Character.toUpperCase(tagName.charAt(0)) + tagName.substring(1) + "GspTagLib";
        filePath = packageName.replace('.', File.separatorChar) + File.separatorChar + getTagLibFileName();
        this.text = text;
    }

    public GspTagInfo(File file) {
        try {
            String path = file.getCanonicalPath();
            if (path.indexOf("grails-app" + File.separatorChar + "taglib" + File.separatorChar) > -1) {
                path = path.substring(path.indexOf("grails-app" + File.separatorChar + "taglib" + File.separatorChar) + 18);
            }
            if (path.lastIndexOf(".gsp") > -1) {
                path = path.substring(0, path.lastIndexOf(".gsp"));
            }
            int lastDot = path.lastIndexOf(File.separatorChar);
            if (lastDot > -1) {
                packageName = path.substring(0, lastDot).replace(File.separatorChar, '.');
                tagName = path.substring(lastDot + 1);
            } else {
                packageName = "";
                tagName = path;
            }
            tagLibName = "_" + Character.toUpperCase(tagName.charAt(0)) + tagName.substring(1) + "GspTagLib";
            text = read(file);
            filePath = path + File.separatorChar + getTagLibFileName();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String getTagLibFQCN() {
        return packageName + '.' + tagLibName;
    }

    public String getTagLibFileName() {
        return getTagLibName() + ".groovy";
    }

    public String getFilePath() {
        return filePath;
    }

    public String getTagName() {
        return tagName;
    }

    public String getTagLibName() {
        return tagLibName;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getText() {
        return text;
    }

    private String read(File file) {
        try {
            return IOUtils.toString(new FileInputStream(file), gspEncoding);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String getGspEncoding() {
        return gspEncoding;
    }

    public void setGspEncoding(String gspEncoding) {
        this.gspEncoding = gspEncoding;
    }
}
