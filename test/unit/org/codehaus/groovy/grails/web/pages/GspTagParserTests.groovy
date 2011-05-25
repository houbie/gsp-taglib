package org.codehaus.groovy.grails.web.pages

class GspTagParserTests extends GroovyTestCase {
    static final testResourcesDir = new File('./test/unit/org/codehaus/groovy/grails/web/pages')



    void testParseStaticContent() {
        String gsp = '<p>static content</p>'
        GspTagInfo tagInfo= new GspTagInfo('staticContent', 'test.gspparser', gsp)
        GroovyPageParser parser = new GspTagParser(tagInfo)
        String parseResult = parser.parse().text
        assert parseResult.trim() == '''package test.gspparser


class _StaticContentGspTagLib {
def staticContent = { attrs, body ->
out.print("<p>static content</p>")
}
}'''
    }

    void testParseStaticContentWithIncludesAndExpressions() {
        String gsp = '''
<%@ page import="java.lang.System" %>
<%@ page import="java.lang.String" %>

<p>${System.getenv('foo')}</p>
<p>${x ?: "default"}</p>
'''
        GspTagInfo tagInfo= new GspTagInfo('staticContentWithIncludesAndExpressions', 'test.gspparser', gsp)
        GroovyPageParser parser = new GspTagParser(tagInfo)
        String parseResult = parser.parse().text
        assert parseResult.trim() == '''package test.gspparser

import java.lang.System
import java.lang.String

class _StaticContentWithIncludesAndExpressionsGspTagLib {
def staticContentWithIncludesAndExpressions = { attrs, body ->
out.print("\\n")
out.print("\\n")
out.print("\\n\\n<p>")
out.print(System.getenv('foo'))
out.print("</p>\\n<p>")
out.print(x ?: "default")
out.print("</p>\\n")
}
}'''
    }

    void testParseNestedTags() {
        String gsp = '''
<g:if test="${attrs.condition==true}">
<g:each in="${attrs.beans}" var="bean">
${bean}
</g:each>
</g:if>
<div>${body()}</div>
'''
        GspTagInfo tagInfo= new GspTagInfo('nestedTags', 'test.gspparser', gsp)
        GroovyPageParser parser = new GspTagParser(tagInfo)
        String parseResult = parser.parse().text
        assert parseResult.trim() == '''package test.gspparser


class _NestedTagsGspTagLib {
def nestedTags = { attrs, body ->
out.print("\\n")
if(true && (attrs.condition==true)) {
out.print("\\n")
attrs.beans.each { bean ->
out.print("\\n")
out.print(bean)
out.print("\\n")
}
out.print("\\n")
}
out.print("\\n<div>")
out.print(body())
out.print("</div>\\n")
}
}'''
    }

    void testParseNamespace() {
        String gsp = '''
<%@ page namespace="ns" %>
<div>${body()}</div>
'''
        GspTagInfo tagInfo= new GspTagInfo('namespaceTag', 'test.gspparser', gsp)
        GroovyPageParser parser = new GspTagParser(tagInfo)
        String parseResult = parser.parse().text
        assert parseResult.trim() == '''package test.gspparser


class _NamespaceTagGspTagLib {
static namespace = "ns"
def namespaceTag = { attrs, body ->
out.print("\\n")
out.print("\\n<div>")
out.print(body())
out.print("</div>\\n")
}
}'''
    }

    void testParseComment() {
        String gsp = '''
<%--/**
     *
     * @attr name REQUIRED
     */--%>
<p>${attrs.name}</p>'''
        GspTagInfo tagInfo= new GspTagInfo('staticContent', 'test.gspparser', gsp)
        GroovyPageParser parser = new GspTagParser(tagInfo)
        String parseResult = parser.parse().text
        println parseResult
        assert parseResult.trim() =='''package test.gspparser


class _StaticContentGspTagLib {
/**
     *
     * @attr name REQUIRED
     */
def staticContent = { attrs, body ->
out.print("\\n\\n<p>")
out.print(attrs.name)
out.print("</p>")
}
}'''
    }
}
