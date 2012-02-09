package be.ixor.grails.gsptaglib

import grails.test.GroovyPagesTestCase


class LayoutWriterStackTests extends GroovyPagesTestCase {
    def template= "<g:twoColumn><g:left>leftContent</g:left><g:right>rightContent</g:right>bodyContent</g:twoColumn>"

    void testLayoutTag() {
        assertOutputEquals("<div class='twoColumn'>left: <div class='left'>leftContent</div>, right: <div class='right'>rightContent</div>, body: bodyContent</div>",
                template, [:])
    }

    void testNestedLayoutTag() {
        def nested = template.replaceAll("leftContent", template)

        assertOutputEquals("<div class='twoColumn'>left: <div class='left'><div class='twoColumn'>left: <div class='left'>leftContent</div>, right: <div class='right'>rightContent</div>, body: bodyContent</div></div>, right: <div class='right'>rightContent</div>, body: bodyContent</div>",
                nested, [:])
    }
}
