package org.gradle.profiler.mutations

class ApplyChangeToPropertyResourceFileMutatorTest extends AbstractMutatorTest {
    public static final String ORIGINAL_CONTENTS = "org.foo=bar"

    def "adds and removes property to end of source file"() {
        def sourceFile = tmpDir.newFile("test.properties")
        sourceFile.text = ORIGINAL_CONTENTS
        def mutator = new ApplyChangeToPropertyResourceFileMutator(sourceFile)
        mutator.timestamp = 1234

        when:
        mutator.beforeBuild(buildContext)

        then:
        sourceFile.text == 'org.foo=bar\norg.acme.some=_1234_1\n'

        when:
        mutator.beforeBuild(buildContext)

        then:
        sourceFile.text == 'org.foo=bar\norg.acme.some=_1234_2\n'

        when:
        mutator.beforeBuild(buildContext)

        then:
        sourceFile.text == 'org.foo=bar\norg.acme.some=_1234_3\n'
    }

    def "reverts changes when nothing has been applied"() {
        def sourceFile = tmpDir.newFile("test.properties")
        sourceFile.text = ORIGINAL_CONTENTS
        def mutator = new ApplyChangeToPropertyResourceFileMutator(sourceFile)

        when:
        mutator.afterScenario(scenarioContext)

        then:
        sourceFile.text == ORIGINAL_CONTENTS
    }

    def "reverts changes when changes has been applied"() {
        def sourceFile = tmpDir.newFile("test.properties")
        sourceFile.text = ORIGINAL_CONTENTS
        def mutator = new ApplyChangeToPropertyResourceFileMutator(sourceFile)

        when:
        mutator.beforeBuild(buildContext)
        mutator.afterScenario(scenarioContext)

        then:
        sourceFile.text == ORIGINAL_CONTENTS
    }
}
