includeTargets << grailsScript("_GrailsCompile")

def gspTagInfoClass
def gspTagParserClass


eventCompileEnd = {
    if (generateTagLibSources()) {
        println 'compiling generated gsp tags'
        compile()
    }
}

generateTagLibSources = {
    boolean generatedFiles = false
    if (!gspTagInfoClass) {
        loadClasses()
    }
    if (gspTagInfoClass && gspTagParserClass) {
        println 'generating code for gsp tags'
        def gspTags = resolveResources("file:${basedir}/**/grails-app/taglib/**/*.gsp")
        for (gsp in gspTags.file) {
            generatedFiles |= generateTagLibSource(gsp)
        }
    }
    return generatedFiles
}

generateTagLibSource = {gsp, force = false ->
    def tagInfo = createGspTagInfo(gsp)
    File destination = new File(gsp.parent, tagInfo.tagLibFileName)
    if (force || !destination.exists() || destination.lastModified() < gsp.lastModified()) {
        println "generating taglib code for $gsp"
        def parser = createGspTagParser(tagInfo)
        if (buildProps["gsptaglib.addRequiredAsserts"] != null) {
            parser.addRequiredAsserts = buildProps["gsptaglib.addRequiredAsserts"]
        }
        destination.text = parser.parse().text
        return true
    }
    return false
}

createGspTagInfo = {file ->
    gspTagInfoClass?.newInstance([file] as Object[])
}

createGspTagParser = {tagInfo ->
    gspTagParserClass?.newInstance([tagInfo] as Object[])
}

loadClasses = {
    gspTagInfoClass = softLoadClass('org.codehaus.groovy.grails.web.pages.GspTagInfo')
    gspTagParserClass = softLoadClass('org.codehaus.groovy.grails.web.pages.GspTagParser')
}

softLoadClass = { className ->
    try {
        classLoader.loadClass(className)
    } catch (ClassNotFoundException e) {
        null
    }
}
