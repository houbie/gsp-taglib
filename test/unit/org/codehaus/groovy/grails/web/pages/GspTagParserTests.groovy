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
// Generated code, DO NOT EDIT!

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
        println parseResult
        assertEquals(parseResult.trim(), '''package test.gspparser
// Generated code, DO NOT EDIT!

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
        println parseResult
        assertEquals(parseResult.trim(), '''package test.gspparser
// Generated code, DO NOT EDIT!

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
        println parseResult
        assertEquals(parseResult.trim(), '''package test.gspparser
// Generated code, DO NOT EDIT!

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
        println parseResult
        assertEquals(parseResult.trim(), '''package test.gspparser
// Generated code, DO NOT EDIT!

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
// Generated code, DO NOT EDIT!

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

    void testParseRequired() {
        String gsp = '''
<%@ page required="name , firstName ,address\t,street" %>
<%@ page required=" foo, " %>

<p>${attrs.name}</p>'''
        GspTagInfo tagInfo = new GspTagInfo('required', 'test.gspparser', gsp)
        GroovyPageParser parser = new GspTagParser(tagInfo)
        String parseResult = parser.parse().text
        println parseResult
        assertEquals(parseResult.trim(), '''package test.gspparser
// Generated code, DO NOT EDIT!

import org.codehaus.groovy.grails.web.taglib.*

class _RequiredGspTagLib {
def required = { attrs, body ->
assert attrs.name, "missing required attribute name"
assert attrs.firstName, "missing required attribute firstName"
assert attrs.address, "missing required attribute address"
assert attrs.street, "missing required attribute street"
assert attrs.foo, "missing required attribute foo"
out.print('\\n')
out.print('\\n')
out.print('\\n\\n<p>')
out.print(attrs.name)
out.print('</p>')
}
}''')
    }

    void testParseMultipleBodies() {
        String gsp = '''
<g:if test="${aTest}">
    <g:outer1 name="outer1">
        <g:inner1 name="1">inner1text</g:inner1>
        <g:inner2 name="2">
            <n:nested1 name="nested1"/>
            <n:nested2 name="nested2">
                <i:innerNested name="innerNested"/>
            </n:nested2>
        </g:inner2>
        <g:inner3 name="3">inner3text</g:inner3>
    </g:outer1>
    <g:outer2 name="outer2">
        <g:inner1 name="1">inner1text</g:inner1>
    </g:outer2>
</g:if>
'''
        GspTagInfo tagInfo = new GspTagInfo('multipleBodies', 'test.gspparser', gsp)
        GroovyPageParser parser = new GspTagParser(tagInfo)
        String parseResult = parser.parse().text
        println parseResult
        assertEquals(parseResult.trim(), '''package test.gspparser
// Generated code, DO NOT EDIT!

import org.codehaus.groovy.grails.web.taglib.*

class _MultipleBodiesGspTagLib {
def multipleBodies = { attrs, body ->
out.print('\\n')
if(true && (aTest)) {
out.print('\\n    ')
def body2 = new GroovyPageTagBody(this,webRequest, {
out.print('\\n        ')
def body3 = 'inner1text'
out.print(g.inner1(['name':("1")] as GroovyPageAttributes,body3))
out.print('\\n        ')
body3 = new GroovyPageTagBody(this,webRequest, {
out.print('\\n            ')
out.print(n.nested1(['name':("nested1")] as GroovyPageAttributes,null))
out.print('\\n            ')
def body4 = new GroovyPageTagBody(this,webRequest, {
out.print('\\n                ')
out.print(i.innerNested(['name':("innerNested")] as GroovyPageAttributes,null))
out.print('\\n            ')
})
out.print(n.nested2(['name':("nested2")] as GroovyPageAttributes,body4))
out.print('\\n        ')
})
out.print(g.inner2(['name':("2")] as GroovyPageAttributes,body3))
out.print('\\n        ')
body3 = 'inner3text'
out.print(g.inner3(['name':("3")] as GroovyPageAttributes,body3))
out.print('\\n    ')
})
out.print(g.outer1(['name':("outer1")] as GroovyPageAttributes,body2))
out.print('\\n    ')
body2 = new GroovyPageTagBody(this,webRequest, {
out.print('\\n        ')
def body3 = 'inner1text'
out.print(g.inner1(['name':("1")] as GroovyPageAttributes,body3))
out.print('\\n    ')
})
out.print(g.outer2(['name':("outer2")] as GroovyPageAttributes,body2))
out.print('\\n')
}
out.print('\\n')
}
}''')
    }

    void testTagWithoutAttributes() {
        String gsp = '''
<g:outer>
    <g:inner1>inner1</g:inner1>
    <g:inner2 >
        <n:nested1/>
        <n:nested2 >nested2</n:nested2>
    </g:inner2>
    <g:inner3>inner3</g:inner3>
</g:outer>
'''
        GspTagInfo tagInfo = new GspTagInfo('noAttributes', 'test.gspparser', gsp)
        GroovyPageParser parser = new GspTagParser(tagInfo)
        String parseResult = parser.parse().text
        println parseResult
        assertEquals(parseResult.trim(), '''package test.gspparser
// Generated code, DO NOT EDIT!

import org.codehaus.groovy.grails.web.taglib.*

class _NoAttributesGspTagLib {
def noAttributes = { attrs, body ->
out.print('\\n')
def body1 = new GroovyPageTagBody(this,webRequest, {
out.print('\\n    ')
def body2 = 'inner1'
out.print(g.inner1([:],body2))
out.print('\\n    ')
body2 = new GroovyPageTagBody(this,webRequest, {
out.print('\\n        ')
out.print(n.nested1([:],null))
out.print('\\n        ')
def body3 = 'nested2'
out.print(n.nested2([:],body3))
out.print('\\n    ')
})
out.print(g.inner2([:],body2))
out.print('\\n    ')
body2 = 'inner3'
out.print(g.inner3([:],body2))
out.print('\\n')
})
out.print(g.outer([:],body1))
out.print('\\n')
}
}''')
    }
}
