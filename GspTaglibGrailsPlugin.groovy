import org.codehaus.groovy.grails.web.pages.GspTagInfo
import org.codehaus.groovy.grails.web.pages.GspTagParser

class GspTaglibGrailsPlugin {
    // the plugin version
    def version = "0.1"
    // the version or versions of Grails the plugin is designed for
    def grailsVersion = "1.2.1 > *"
    // the other plugins this plugin depends on
    def dependsOn = [:]
    // resources that are excluded from plugin packaging
    def pluginExcludes = [
            "grails-app/views/error.gsp"
    ]

    // TODO Fill in these fields
    def author = "Ivo Houbrechts"
    def authorEmail = "ivo@houbrechts-it.be"
    def title = "GSP tags"
    def description = "Grails plugin that makes it possible to define tags in GSP's in the grails-app/taglib"

    // URL to the plugin's documentation
    def documentation = "http://grails.org/plugin/gsp-taglib"

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
