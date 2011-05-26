package org.codehaus.groovy.grails.web.pages

class GspTagParserTests extends GroovyTestCase {
    static final testResourcesDir = new File('./test/unit/org/codehaus/groovy/grails/web/pages')



    void testParseStaticContent() {
        String gsp = '<p>static content</p>'
        GspTagInfo tagInfo = new GspTagInfo('staticContent', 'test.gspparser', gsp)
        GroovyPageParser parser = new GspTagParser(tagInfo)
        String parseResult = parser.parse().text
        println parseResult

        assertEquals(parseResult.trim(), '''package test.gspparser

import org.codehaus.groovy.grails.web.taglib.*

class _StaticContentGspTagLib {
def staticContent = { attrs, body ->
out.print('<p>static content</p>')
}
}''')
    }

    void testParseStaticContentWithIncludesAndExpressions() {
        String gsp = '''
<%@ page import="java.lang.System" %>
<%@ page import="java.lang.String" %>

<p>${System.getenv('foo')}</p>
<p>${x ?: "default"}</p>
<p>${x ?: 'otherDefault'}</p>
'''
        GspTagInfo tagInfo = new GspTagInfo('staticContentWithIncludesAndExpressions', 'test.gspparser', gsp)
        GroovyPageParser parser = new GspTagParser(tagInfo)
        String parseResult = parser.parse().text
        assertEquals(parseResult.trim(), '''package test.gspparser

import java.lang.System
import java.lang.String
import org.codehaus.groovy.grails.web.taglib.*

class _StaticContentWithIncludesAndExpressionsGspTagLib {
def staticContentWithIncludesAndExpressions = { attrs, body ->
out.print('\\n')
out.print('\\n')
out.print('\\n\\n<p>')
out.print(System.getenv('foo'))
out.print('</p>\\n<p>')
out.print(x ?: "default")
out.print('</p>\\n<p>')
out.print(x ?: 'otherDefault')
out.print('</p>\\n')
}
}''')
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
        GspTagInfo tagInfo = new GspTagInfo('nestedTags', 'test.gspparser', gsp)
        GroovyPageParser parser = new GspTagParser(tagInfo)
        String parseResult = parser.parse().text
        assertEquals(parseResult.trim(), '''package test.gspparser

import org.codehaus.groovy.grails.web.taglib.*

class _NestedTagsGspTagLib {
def nestedTags = { attrs, body ->
out.print('\\n')
if(true && (attrs.condition==true)) {
out.print('\\n')
attrs.beans.each { bean ->
out.print('\\n')
out.print(bean)
out.print('\\n')
}
out.print('\\n')
}
out.print('\\n<div>')
out.print(body())
out.print('</div>\\n')
}
}''')
    }

    void testParseQuotesAndTags() {
        String gsp = '''
<p class="para">
    <t:someTag attr="value">
       <t:nested nestedAttr="${nestedVal}">
          <span class="myspan" id='myid'>xxx</span>
        </t:nested>
    </t:someTag>
</p>
'''
        GspTagInfo tagInfo = new GspTagInfo('quotesAndTags', 'test.gspparser', gsp)
        GroovyPageParser parser = new GspTagParser(tagInfo)
        String parseResult = parser.parse().text
        assertEquals(parseResult.trim(), '''package test.gspparser

import org.codehaus.groovy.grails.web.taglib.*

class _QuotesAndTagsGspTagLib {
def quotesAndTags = { attrs, body ->
out.print('\\n<p class="para">\\n    ')
def body1 = new GroovyPageTagBody(this,webRequest, {
out.print('\\n       ')
def body2 = '\\n          <span class="myspan" id=\\'myid\\'>xxx</span>\\n        '
out.print(t.nested(['nestedAttr':(nestedVal)] as GroovyPageAttributes,body2))
out.print('\\n    ')
})
out.print(t.someTag(['attr':("value")] as GroovyPageAttributes,body1))
out.print('\\n</p>\\n')
}
}''')
    }

    void testParseNamespace() {
        String gsp = '''
<%@ page namespace="ns" %>
<div>${body()}</div>
'''
        GspTagInfo tagInfo = new GspTagInfo('namespaceTag', 'test.gspparser', gsp)
        GroovyPageParser parser = new GspTagParser(tagInfo)
        String parseResult = parser.parse().text
        assertEquals(parseResult.trim(), '''package test.gspparser

import org.codehaus.groovy.grails.web.taglib.*

class _NamespaceTagGspTagLib {
static namespace = "ns"
def namespaceTag = { attrs, body ->
out.print('\\n')
out.print('\\n<div>')
out.print(body())
out.print('</div>\\n')
}
}''')
    }

    void testParseComment() {
        String gsp = '''
<%--/**
     *
     * @attr name REQUIRED
     */--%>
<p>${attrs.name}</p>'''
        GspTagInfo tagInfo = new GspTagInfo('staticContent', 'test.gspparser', gsp)
        GroovyPageParser parser = new GspTagParser(tagInfo)
        String parseResult = parser.parse().text
        println parseResult
        assertEquals(parseResult.trim(), '''package test.gspparser

import org.codehaus.groovy.grails.web.taglib.*

class _StaticContentGspTagLib {
/**
     *
     * @attr name REQUIRED
     */
def staticContent = { attrs, body ->
out.print('\\n\\n<p>')
out.print(attrs.name)
out.print('</p>')
}
}''')
    }
}
