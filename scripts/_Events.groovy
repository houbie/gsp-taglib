includeTargets << grailsScript("_GrailsCompile")

def gspTagInfoClass
def gspTagParserClass
String generatedSrc = 'generated-sources'
String generatedTagSrc = "$generatedSrc/grails-app/taglib"
File genTagSrcDir = new File("$projectTargetDir/$generatedTagSrc")

eventCleanStart = {
    ant.delete(dir: "$projectTargetDir/$generatedSrc")
}

eventCompileStart = {
    if (!projectCompiler.srcDirectories.find {it.endsWith(generatedTagSrc)}) {
        genTagSrcDir.mkdirs()
        projectCompiler.srcDirectories << genTagSrcDir.absolutePath
    }
}

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
    File destinationDir = new File(genTagSrcDir, tagInfo.packageName.replace('.', '/'))
    File destination = new File(destinationDir, tagInfo.tagLibFileName)
    if (force || !destination.exists() || destination.lastModified() < gsp.lastModified()) {
        println "generating taglib code for $gsp"
        destinationDir.mkdirs()
        def parser = createGspTagParser(tagInfo)
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
