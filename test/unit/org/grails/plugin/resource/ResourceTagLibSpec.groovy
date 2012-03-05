package org.grails.plugin.resource

import grails.plugin.spock.TagLibSpec
import grails.util.Environment
import org.grails.plugin.resource.util.HalfBakedLegacyLinkGenerator
import org.codehaus.groovy.grails.web.mapping.DefaultLinkGenerator
import grails.test.mixin.TestFor
import spock.lang.IgnoreRest
import org.apache.log4j.Logger
import org.gmock.WithGMock
import spock.lang.Specification
import grails.test.mixin.Mock
import grails.plugin.spock.UnitSpec
import org.apache.commons.logging.Log
import static junit.framework.Assert.assertFalse
import static junit.framework.Assert.assertTrue
import static junit.framework.Assert.assertNotNull
import static junit.framework.Assert.assertEquals
import spock.lang.Unroll
import org.codehaus.groovy.grails.commons.cfg.ConfigurationHelper
import org.codehaus.groovy.grails.commons.ConfigurationHolder
import static org.codehaus.groovy.grails.commons.ConfigurationHolder.setConfig

@TestFor(ResourceTagLib)
class ResourceTagLibSpec extends Specification {

    def r

    def setup() {
        Object.metaClass.encodeAsHTML = { -> delegate.toString() }
        tagLib.request.contextPath = ""
        r = getResourceMetadata('/images/favicon.ico')
        tagLib.grailsResourceProcessor = Mock(ResourceProcessor)

        tagLib.metaClass.getLog =  { -> log}
    }

    def 'testLinkResolutionForGrails2'() {
        given:

            def contextPath = '/CTX'
            def dir = 'images'
            def file = 'favicon.ico'
            tagLib.request.contextPath = contextPath
            tagLib.grailsLinkGenerator = Mock(DefaultLinkGenerator)
        when:
            def res = tagLib.resolveResourceAndURI(dir: dir, file: file)
        then:
            1 * tagLib.grailsResourceProcessor.getResourceMetaForURI('/images/favicon.ico',_,_,_) >> r
            1 * tagLib.grailsResourceProcessor.staticUrlPrefix >> '/static'
            1 * tagLib.grailsResourceProcessor.isDebugMode(_) >> false
            1 * tagLib.grailsLinkGenerator.resource(_) >> "${contextPath}/${dir}/${file}"
            0 * _._

        and:
            "/CTX/static/images/favicon.ico" == res.uri
    }

    def 'testLinkResolutionForGrails2ResourceExcluded'() {

        given:
            def contextPath = '/CTX'
            def dir = 'images'
            def file = 'favicon.ico'
            tagLib.request.contextPath = contextPath
            tagLib.grailsLinkGenerator = Mock(DefaultLinkGenerator)
        when:
            def res = tagLib.resolveResourceAndURI(dir: dir, file: file)
        then:
            1 * tagLib.grailsResourceProcessor.getResourceMetaForURI('/images/favicon.ico',_,_,_) >> null
            1 * tagLib.grailsResourceProcessor.isDebugMode(_) >> false
            1 * tagLib.grailsLinkGenerator.resource(_) >> "${contextPath}/${dir}/${file}"
            0 * _._

        and:
            "/CTX/images/favicon.ico" == res.uri
    }
    
    def 'testLinkResolutionForGrails1_3AndEarlier'() {

        given:
            def contextPath = '/CTX'
            def dir = 'images'
            def file = 'favicon.ico'
            tagLib.request.contextPath = contextPath
            tagLib.grailsLinkGenerator = Mock(HalfBakedLegacyLinkGenerator)
        when:
            def res = tagLib.resolveResourceAndURI(dir: dir, file: file)
        then:
            1 * tagLib.grailsResourceProcessor.getResourceMetaForURI('/images/favicon.ico',_,_,_) >> r
            1 * tagLib.grailsResourceProcessor.staticUrlPrefix >> '/static'
            1 * tagLib.grailsResourceProcessor.isDebugMode(_) >> false
            1 * tagLib.grailsLinkGenerator.resource(_) >> "${contextPath}/${dir}/${file}"
            0 * _._

        and:
            "/CTX/static/images/favicon.ico" == res.uri
    }
    
    def 'testAbsoluteDirFileLinkResolution'() {

        given:
            def contextPath = '/CTX'
            def dir = 'images'
            def file = 'default-avatar.png'
            tagLib.request.contextPath = contextPath
            tagLib.grailsLinkGenerator = Mock(DefaultLinkGenerator)
        when:
            def res = tagLib.resolveResourceAndURI(absolute:true,dir: dir, file: file)
        then:
            1 * tagLib.grailsResourceProcessor.isDebugMode(_) >> false
            1 * tagLib.grailsLinkGenerator.resource(_) >> "http://myserver.com${contextPath}/static/${dir}/${file}"
            0 * _._

        and:
            "http://myserver.com/CTX/static/images/default-avatar.png" == res.uri
    }

    def 'testResourceLinkWithRelOverride'() {

        given:
            def contextPath = '/CTX'

            tagLib.request.contextPath = contextPath
            tagLib.grailsLinkGenerator = Mock(DefaultLinkGenerator)

            def testMeta = getResourceMetadata('/css/test.less')
            testMeta.disposition = 'head'
        and:
            Environment.metaClass.static.getCurrentEnvironment = { -> return Environment.CUSTOM}

        when:
            def output = tagLib.external(uri:'/css/test.less', rel:'stylesheet/less', type:'css').toString()

        then:

            1 * tagLib.grailsResourceProcessor.getResourceMetaForURI(_,_,_,_) >> testMeta
            1 * tagLib.grailsResourceProcessor.isDebugMode(_) >> false
            1 * tagLib.grailsResourceProcessor.staticUrlPrefix >> '/static'
            0 * _._

        and:
            output.contains('rel="stylesheet/less"')
            output.contains('href="/static/css/test.less"')
    }
    
    def 'testResourceLinkWithRelOverrideFromResourceDecl'() {

        given:
            def testMeta = getResourceMetadata('/css/test.less')
            testMeta.contentType = "stylesheet/less"
            testMeta.disposition = 'head'
            testMeta.tagAttributes = [rel:'stylesheet/less']

        when:
            def output = tagLib.external(uri:'/css/test.less', type:'css').toString()
        then:
            1 * tagLib.grailsResourceProcessor.getResourceMetaForURI(_,_,_,_) >> testMeta
            1 * tagLib.grailsResourceProcessor.isDebugMode(_) >> false
            1 * tagLib.grailsResourceProcessor.staticUrlPrefix >> '/static'
            0 * _._
        and:
            println "Output was: $output"
            assertTrue output.contains('rel="stylesheet/less"')
            assertTrue output.contains('href="/static/css/test.less"')
    }

    def 'testResourceLinkWithWrapperAttribute'() {

        given:
            def testMeta = getResourceMetadata('/css/ie.less')
            testMeta.contentType = 'text/css'
            testMeta.disposition = 'head'
            testMeta.tagAttributes = [rel:'stylesheet']

        when:
            def output = tagLib.external(uri:'/css/ie.less', type:'css', wrapper: { s -> "WRAPPED${s}WRAPPED" }).toString()
        then:
            1 * tagLib.grailsResourceProcessor.getResourceMetaForURI(_,_,_,_) >> testMeta
            1 * tagLib.grailsResourceProcessor.isDebugMode(_) >> false
            1 * tagLib.grailsResourceProcessor.staticUrlPrefix >> '/static'
            0 * _._
        and:
            println "Output was: $output"
            assertTrue output.contains('rel="stylesheet"')
            assertFalse "Should not contain the wrapper= attribute in output", output.contains('wrapper=')
            assertTrue output.contains('WRAPPED<link')
            assertTrue output.contains('/>WRAPPED')
    }

    def 'testRenderModuleWithNonExistentResource'() {
        given:
            def testMeta = getResourceMetadata('/this/is/bull.css')
            testMeta.contentType = 'test/stylesheet'
            testMeta.disposition = 'head'
            testMeta._resourceExists = false
            testMeta.tagAttributes = [rel:'stylesheet']
            def expectedModuleName = 'test'

        and:
            def testMod = new ResourceModule()
            testMod.resources << testMeta


        when:
            def output = tagLib.renderModule(name: expectedModuleName).toString()

        then:
            1 * tagLib.grailsResourceProcessor.isDebugMode(_) >> false
            1 * tagLib.grailsResourceProcessor.getModule(_) >> testMod
            0 * _._

        and:
            thrown(IllegalArgumentException)
    }

    def 'testImgTagWithAttributes'() {
        given:
            def testMeta  = getResourceMetadata('/images/test.png')
            testMeta.contentType = 'image/png'
            testMeta.disposition = 'head'
            testMeta.tagAttributes = [width:'100', height:'50', alt:'mugshot']

        when:
            def output = tagLib.img(uri:'/images/test.png').toString()
        then:
            1 * tagLib.grailsResourceProcessor.getResourceMetaForURI(_,_,_,_) >> testMeta
            1 * tagLib.grailsResourceProcessor.isDebugMode(_) >> false
            1 * tagLib.grailsResourceProcessor.staticUrlPrefix >> '/static'
            0 * _._

        and:
            println "Output was: $output"
            assertTrue output.contains('width="100"')
            assertTrue output.contains('height="50"')
            assertTrue output.contains('alt="mugshot"')
            assertTrue output.contains('src="/static/images/test.png"')
            assertFalse output.contains('uri=')
    }

    def 'testImgTagWithAttributesDefaultDir'() {
        given:
            def dir = 'images'
            def file = 'favicon.ico'
            tagLib.grailsLinkGenerator = Mock(DefaultLinkGenerator)
        and:
            def testMeta = getResourceMetadata('/images/test.png')
            testMeta.contentType = 'image/png'
            testMeta.disposition = 'head'
            testMeta.tagAttributes = [width:'100', height:'50', alt:'mugshot']
        when:
            def output = tagLib.img(file:'test.png').toString()

        then:
            1 * tagLib.grailsResourceProcessor.getResourceMetaForURI(_,_,_,_) >> testMeta
            1 * tagLib.grailsResourceProcessor.isDebugMode(_) >> false
            1 * tagLib.grailsResourceProcessor.staticUrlPrefix >> '/static'
            1 * tagLib.grailsLinkGenerator.resource(_) >> "${dir}/${file}"
            0 * _._

        and:
            println "Output was: $output"
            assertTrue output.contains('width="100"')
            assertTrue output.contains('height="50"')
            assertTrue output.contains('src="/static/images/test.png"')
            assertFalse output.contains('file=')
            assertFalse output.contains('dir=')
    }

    def 'testDebugModeResourceLinkWithAbsoluteCDNURL'() {
        given:
            def url = 'https://ajax.googleapis.com/ajax/libs/jquery/1.4/jquery.min.js'
            def testMeta = getResourceMetadata(url)
            testMeta.disposition = 'head'
        and:
            tagLib.request.contextPath = "/resourcestests"

        when:
            def output = tagLib.external(uri:url, type:"js").toString()
        then:
            1 * tagLib.grailsResourceProcessor.isDebugMode(_) >> true
            0 * _._
        and:
            println "Output was: $output"
            assertTrue output.contains('src="https://ajax.googleapis.com/ajax/libs/jquery/1.4/jquery.min.js?_debugResources')
    }

    def 'testRequireUpdatesRequestAttributes'() {

        when:
            def output = tagLib.require(modules:['thingOne', 'thingTwo']).toString()

        then:
           1 * tagLib.grailsResourceProcessor.addModuleDispositionsToRequest(tagLib.request,'thingOne')
           1 * tagLib.grailsResourceProcessor.addModuleDispositionsToRequest(tagLib.request,'thingTwo')
           1 * tagLib.grailsResourceProcessor.addModuleDispositionsToRequest(tagLib.request,ResourceProcessor.IMPLICIT_MODULE)
           0 * _._

        and:
            def tracker = tagLib.request.resourceModuleTracker
            assertNotNull tracker
            assertEquals 3, tracker?.size()
            assertTrue tracker.containsKey('thingOne')
            assertEquals true, tracker.thingOne
            assertTrue tracker.containsKey('thingTwo')
            assertEquals true, tracker.thingOne
            assertTrue tracker.containsKey(ResourceProcessor.IMPLICIT_MODULE)
            assertEquals false, tracker[ResourceProcessor.IMPLICIT_MODULE]
    }
    
    def 'testRequireIndicatesModuleNotMandatory'() {
        when:
            def output = tagLib.require(modules:['thingOne', 'thingTwo'], strict:false).toString()

        then:
           1 * tagLib.grailsResourceProcessor.addModuleDispositionsToRequest(tagLib.request,'thingOne')
           1 * tagLib.grailsResourceProcessor.addModuleDispositionsToRequest(tagLib.request,'thingTwo')
           1 * tagLib.grailsResourceProcessor.addModuleDispositionsToRequest(tagLib.request,ResourceProcessor.IMPLICIT_MODULE)
           0 * _._
        and:
            def tracker = tagLib.request.resourceModuleTracker
            assertNotNull tracker
            assertEquals 3, tracker?.size()
            assertTrue tracker.containsKey('thingOne')
            assertEquals false, tracker.thingOne
            assertTrue tracker.containsKey('thingTwo')
            assertEquals false, tracker.thingTwo
            assertTrue tracker.containsKey(ResourceProcessor.IMPLICIT_MODULE)
            assertEquals false, tracker[ResourceProcessor.IMPLICIT_MODULE]
    }

    @Unroll({"DoResourceLinks should return context /CTX link in uri ${expectedUri} for expected environment "})
    def 'testDoResourceLinkReturnsLinksWithContextInExpectedEnvironments'() {

        given:
            Environment.metaClass.static.getCurrentEnvironment = { -> return environment}
            tagLib.request.contextPath = "/CTX"

        when:
            def output = tagLib.doResourceLink(uri:'/CTX/css/ie.less', type:'css').toString()

        then:
            assert output.contains("${expectedUri}")

        where:
            environment             | expectedUri
            Environment.DEVELOPMENT | 'href="/CTX/css/ie.less"'
            Environment.TEST        | 'href="/CTX/css/ie.less"'
            Environment.PRODUCTION  | 'href="/css/ie.less"'
            Environment.CUSTOM      | 'href="/css/ie.less"'
    }

    def 'testDoResourceLinkReturnsLinksWithContextInGrailsConfigEnvironments'() {


        given:
            ConfigObject config = new ConfigSlurper().parse('''grails.resource.preprod = ['test']''')
            ConfigurationHolder.config = config
            tagLib.request.contextPath = "/CTX"

        when:
            def output = tagLib.doResourceLink(uri:'/CTX/css/ie.less', type:'css').toString()

        then:
            assert output.contains('href="/CTX/css/ie.less"')

    }



    private ResourceMeta getResourceMetadata(String uri) {
        def r = new ResourceMeta()
        r.with {
            sourceUrl = uri
            actualUrl = uri
        }

        return r

    }

}
