import grails.util.BuildSettingsHolder
import grails.util.Environment
import org.codehaus.groovy.grails.commons.TagLibArtefactHandler
import org.codehaus.groovy.grails.web.pages.GspTagInfo
import org.codehaus.groovy.grails.web.pages.GspTagParser
import org.springframework.core.io.Resource

class GspTaglibGrailsPlugin {
    def version = "2.0"
    def grailsVersion = "2.0.0 > *"
    def dependsOn = [:]
    def loadBefore = ['groovyPages']

    def pluginExcludes = [
            "grails-app/**",
            "web-app/**"
    ]

    def author = "Ivo Houbrechts"
    def authorEmail = "ivo@houbrechts-it.be"
    def title = "GSP tags plugin"
    def description = '''\
Grails plugin that makes it possible to define tags in GSP's in the grails-app/taglib
'''

    def license = "APACHE"
    def organization = [name: "Ixor", url: "http://www.ixor.be"]
    def developers = [[name: "Ivo Houbrechts", email: "ivo@houbrechts-it.be"]]
    def scm = [url: "https://houbie@github.com/houbie/gsp-taglib.git"]

    def documentation = "http://grails-plugins.github.com/grails-release/"

    def watchedResources = [
            "file:./plugins/*/grails-app/taglib/**/*.gsp",
            "file:./grails-app/taglib/**/*.gsp"
    ]


    def doWithSpring = {
        Resource[] resources = resolver.getResources("file:${Environment.current.reloadLocation}/grails-app/taglib/**/*.gsp")
        for (resource in resources) {
            GspTagInfo tagInfo = new GspTagInfo(resource.file)
            application.addArtefact(TagLibArtefactHandler.TYPE, this.class.classLoader.loadClass(tagInfo.tagLibFQCN))
        }
    }

    def onChange = { event ->
        File gsp = event.source.file
        log.info "generating tag for ${gsp.name}"
        GspTagInfo tagInfo = new GspTagInfo(gsp)
        File targetDir = BuildSettingsHolder.settings.projectTargetDir
        String generatedSrc = 'generated-sources'
        String generatedTagSrc = "$generatedSrc/grails-app/taglib"
        File genTagSrcDir = new File(targetDir, generatedTagSrc)
        File destinationDir = new File(genTagSrcDir, tagInfo.packageName.replace('.', '/'))
        File destination = new File(destinationDir, tagInfo.tagLibFileName)
        boolean isNew = !destination.exists()
        destinationDir.mkdirs()
        def parser = new GspTagParser(tagInfo)
        destination.text = parser.parse().text

        if (isNew) {
            Thread.start {
                Class tagLib = null
                int count= 20
                while (!tagLib && count--) {
                    sleep(500)
                    try {
                        tagLib = getClass().classLoader.loadClass(tagInfo.tagLibFQCN)
                        manager.informOfClassChange(tagLib)
                    } catch (ClassNotFoundException e) {}
                }
            }
        }
    }
}
