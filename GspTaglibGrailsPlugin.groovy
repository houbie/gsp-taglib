import org.codehaus.groovy.grails.web.pages.GspTagInfo
import org.codehaus.groovy.grails.web.pages.GspTagParser

class GspTaglibGrailsPlugin {
    def version = "0.2"
    def grailsVersion = "1.3.0 > *"
    def dependsOn = [:]

    def pluginExcludes = [
            "grails-app/views/error.gsp"
    ]

    // TODO Fill in these fields
    def author = "Ivo Houbrechts"
    def authorEmail = "ivo@houbrechts-it.be"
    def title = "GSP tags plugin"
    def description = '''\
Grails plugin that makes it possible to define tags in GSP's in the grails-app/taglib
'''

    def license = "APACHE"
    def organization = [ name: "Ixor", url: "http://www.ixor.be" ]
    def developers = [ [ name: "Ivo Houbrechts", email: "ivo@houbrechts-it.be" ] ]
    def scm = [ url: "https://houbie@github.com/houbie/gsp-taglib.git" ]

    def documentation = "http://grails-plugins.github.com/grails-release/"

    def watchedResources = ["file:./plugins/*/grails-app/taglib/**/*.gsp",
            "file:./grails-app/taglib/**/*.gsp"]

    def onChange = { event ->
        File gsp = event.source.file
        log.info "generating tag for ${gsp.name}"
        GspTagInfo tagInfo = new GspTagInfo(gsp)
        File destination = new File(gsp.parent, tagInfo.tagLibFileName)
        destination.text = new GspTagParser(tagInfo).parse().text
    }
}
