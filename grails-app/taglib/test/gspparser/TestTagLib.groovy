package test.gspparser

import be.ixor.grails.gsptaglib.LayoutWriterStack

class TestTagLib {
    def left = {attrs, body ->
        def w = LayoutWriterStack.currentWriter('left')
        w << "<div class='left'>" << body() << "</div>"
    }

    def right = {attrs, body ->
        def w = LayoutWriterStack.currentWriter('right')
        w << "<div class='right'>" << body() << "</div>"
    }

    def twoColumn = {attrs, body ->
        def parts = LayoutWriterStack.writeParts(body)
        out << "<div class='twoColumn'>left: " << parts.left << ", right: " << parts.right << ", body: " << parts.body << "</div>"
    }
}
