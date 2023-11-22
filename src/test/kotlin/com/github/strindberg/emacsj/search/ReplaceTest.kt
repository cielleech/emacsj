package com.github.strindberg.emacsj.search

import java.awt.event.KeyEvent
import java.awt.event.KeyEvent.CHAR_UNDEFINED
import java.awt.event.KeyEvent.VK_ENTER
import javax.swing.JComponent
import com.github.strindberg.emacsj.mark.MarkHandler
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.testFramework.fixtures.BasePlatformTestCase

private const val ACTION_REPLACE_REGEXP = "com.github.strindberg.emacsj.actions.search.replaceregexp"
private const val ACTION_REPLACE_TEXT = "com.github.strindberg.emacsj.actions.search.replacetext"
private const val ACTION_POP_MARK = "com.github.strindberg.emacsj.actions.mark.popmark"

class ReplaceTest : BasePlatformTestCase() {

    override fun tearDown() {
        ReplaceHandler.delegate?.hide()
        super.tearDown()
    }

    fun `test Simple text replace works`() {
        myFixture.configureByText(FILE, "<caret>foo")
        myFixture.performEditorAction(ACTION_REPLACE_TEXT)

        val textField = ReplaceHandler.delegate!!.ui.textField
        val popup = ReplaceHandler.delegate!!.ui.popup

        textField.text = "foo"
        popup.pressEnter(textField)

        textField.text = "bar"
        popup.pressEnter(textField)

        popup.typeChar('y', textField)

        myFixture.checkResult("bar<caret>")
    }

    fun `test Yes and no while replacing works`() {
        myFixture.configureByText(FILE, "<caret>null () null () null")
        myFixture.performEditorAction(ACTION_REPLACE_TEXT)

        val textField = ReplaceHandler.delegate!!.ui.textField
        val popup = ReplaceHandler.delegate!!.ui.popup

        textField.text = "null"
        popup.pressEnter(textField)

        textField.text = "\"label\""
        popup.pressEnter(textField)

        popup.typeChar('y', textField)
        popup.typeChar('y', textField)
        popup.typeChar('n', textField)

        myFixture.checkResult(""""label" () "label" () null<caret>""")
    }

    fun `test Space and no while replacing works`() {
        myFixture.configureByText(FILE, "<caret>null () null () null")
        myFixture.performEditorAction(ACTION_REPLACE_TEXT)

        val textField = ReplaceHandler.delegate!!.ui.textField
        val popup = ReplaceHandler.delegate!!.ui.popup

        textField.text = "null"
        popup.pressEnter(textField)

        textField.text = "\"label\""
        popup.pressEnter(textField)

        popup.typeChar(' ', textField)
        popup.typeChar(' ', textField)
        popup.typeChar('n', textField)

        myFixture.checkResult(""""label" () "label" () null<caret>""")
    }

    fun `test Different order of yes and no works`() {
        myFixture.configureByText(FILE, "<caret>foo foo foo")
        myFixture.performEditorAction(ACTION_REPLACE_TEXT)

        val textField = ReplaceHandler.delegate!!.ui.textField
        val popup = ReplaceHandler.delegate!!.ui.popup

        textField.text = "foo"
        popup.pressEnter(textField)

        textField.text = "bar"
        popup.pressEnter(textField)

        popup.typeChar('y', textField)
        popup.typeChar('n', textField)
        popup.typeChar('y', textField)

        myFixture.checkResult("bar foo bar<caret>")
    }

    fun `test Replacement is only done within selection`() {
        myFixture.configureByText(FILE, "<caret><selection>foo foo</selection> foo")
        myFixture.performEditorAction(ACTION_REPLACE_TEXT)

        val textField = ReplaceHandler.delegate!!.ui.textField
        val popup = ReplaceHandler.delegate!!.ui.popup

        textField.text = "foo"
        popup.pressEnter(textField)

        textField.text = "bar"
        popup.pressEnter(textField)

        popup.typeChar('y', textField)
        popup.typeChar('y', textField)

        ReplaceHandler.delegate!!.hide()

        myFixture.checkResult("bar bar<caret> foo")
    }

    fun `test Replacement is over after period`() {
        myFixture.configureByText(FILE, "<caret>foo foo foo")
        myFixture.performEditorAction(ACTION_REPLACE_TEXT)

        val textField = ReplaceHandler.delegate!!.ui.textField
        val popup = ReplaceHandler.delegate!!.ui.popup

        textField.text = "foo"
        popup.pressEnter(textField)

        textField.text = "bar"
        popup.pressEnter(textField)

        popup.typeChar('y', textField)
        popup.typeChar('.', textField)

        myFixture.checkResult("bar bar<caret> foo")
    }

    fun `test Exclamation mark replaces everything and with correct case`() {
        myFixture.configureByText(FILE, "<caret>foo Foo FOO")
        myFixture.performEditorAction(ACTION_REPLACE_TEXT)

        val textField = ReplaceHandler.delegate!!.ui.textField
        val popup = ReplaceHandler.delegate!!.ui.popup

        textField.text = "foo"
        popup.pressEnter(textField)

        textField.text = "bar"
        popup.pressEnter(textField)

        popup.typeChar('!', textField)

        myFixture.checkResult("bar Bar BAR<caret>")
    }

    fun `test Upper case in replacement makes replacement dependent on case`() {
        myFixture.configureByText(FILE, "<caret>foo Foo FOO")
        myFixture.performEditorAction(ACTION_REPLACE_TEXT)

        val textField = ReplaceHandler.delegate!!.ui.textField
        val popup = ReplaceHandler.delegate!!.ui.popup

        textField.text = "foo"
        popup.pressEnter(textField)

        textField.text = "BAR"
        popup.pressEnter(textField)

        popup.typeChar('!', textField)

        myFixture.checkResult("BAR<caret> Foo FOO")
    }

    fun `test An upper case letter in source makes replacement dependent on case`() {
        myFixture.configureByText(FILE, "<caret>foo Foo FOO")
        myFixture.performEditorAction(ACTION_REPLACE_TEXT)

        val textField = ReplaceHandler.delegate!!.ui.textField
        val popup = ReplaceHandler.delegate!!.ui.popup

        textField.text = "Foo"
        popup.pressEnter(textField)

        textField.text = "bar"
        popup.pressEnter(textField)

        popup.typeChar('!', textField)

        myFixture.checkResult("foo bar<caret> FOO")
    }

    fun `test An upper case word can be transformed to lower case`() {
        myFixture.configureByText(FILE, "<caret>foo Foo FOO")
        myFixture.performEditorAction(ACTION_REPLACE_TEXT)

        val textField = ReplaceHandler.delegate!!.ui.textField
        val popup = ReplaceHandler.delegate!!.ui.popup

        textField.text = "Foo"
        popup.pressEnter(textField)

        textField.text = "foo"
        popup.pressEnter(textField)

        popup.typeChar('!', textField)

        myFixture.checkResult("foo foo<caret> FOO")
    }

    fun `test A lower case word can be transformed to upper case`() {
        myFixture.configureByText(FILE, "<caret>foo Foo FOO")
        myFixture.performEditorAction(ACTION_REPLACE_TEXT)

        val textField = ReplaceHandler.delegate!!.ui.textField
        val popup = ReplaceHandler.delegate!!.ui.popup

        textField.text = "foo"
        popup.pressEnter(textField)

        textField.text = "Foo"
        popup.pressEnter(textField)

        popup.typeChar('!', textField)

        myFixture.checkResult("Foo<caret> Foo FOO")
    }

    fun `test Regexp replace with back references java style works`() {
        myFixture.configureByText(FILE, "<caret>baaat")
        myFixture.performEditorAction(ACTION_REPLACE_REGEXP)

        val textField = ReplaceHandler.delegate!!.ui.textField
        val popup = ReplaceHandler.delegate!!.ui.popup

        textField.text = "(.)aaa(.)"
        popup.pressEnter(textField)

        textField.text = "$1å$2"
        popup.pressEnter(textField)

        popup.typeChar('y', textField)

        myFixture.checkResult("båt<caret>")
    }

    fun `test Regexp replace with back references traditional style works`() {
        myFixture.configureByText(FILE, "<caret>baaat")
        myFixture.performEditorAction(ACTION_REPLACE_REGEXP)

        val textField = ReplaceHandler.delegate!!.ui.textField
        val popup = ReplaceHandler.delegate!!.ui.popup

        textField.text = "(.)aaa(.)"
        popup.pressEnter(textField)

        textField.text = "\\1å\\2"
        popup.pressEnter(textField)

        popup.typeChar('y', textField)

        myFixture.checkResult("båt<caret>")
    }

    fun `test Replace whole regexp match java style works`() {
        myFixture.configureByText(FILE, "<caret>baat")
        myFixture.performEditorAction(ACTION_REPLACE_REGEXP)

        val textField = ReplaceHandler.delegate!!.ui.textField
        val popup = ReplaceHandler.delegate!!.ui.popup

        textField.text = "a"
        popup.pressEnter(textField)

        textField.text = "$0$0"
        popup.pressEnter(textField)

        popup.typeChar('y', textField)
        popup.typeChar('y', textField)

        myFixture.checkResult("baaaa<caret>t")
    }

    fun `test Replace whole regexp match traditional style works`() {
        myFixture.configureByText(FILE, "<caret>baat")
        myFixture.performEditorAction(ACTION_REPLACE_REGEXP)

        val textField = ReplaceHandler.delegate!!.ui.textField
        val popup = ReplaceHandler.delegate!!.ui.popup

        textField.text = "a"
        popup.pressEnter(textField)

        textField.text = "\\&\\&"
        popup.pressEnter(textField)

        popup.typeChar('y', textField)
        popup.typeChar('y', textField)

        myFixture.checkResult("baaaa<caret>t")
    }

    fun `test Replace whole regexp match traditional style and exclamation mark works`() {
        myFixture.configureByText(FILE, "<caret>baat")
        myFixture.performEditorAction(ACTION_REPLACE_REGEXP)

        val textField = ReplaceHandler.delegate!!.ui.textField
        val popup = ReplaceHandler.delegate!!.ui.popup

        textField.text = "a"
        popup.pressEnter(textField)

        textField.text = "\\&\\&"
        popup.pressEnter(textField)

        popup.typeChar('!', textField)

        myFixture.checkResult("baaaa<caret>t")
        ReplaceHandler.delegate = null
    }

    fun `test Regexp replace works with simple text replace`() {
        myFixture.configureByText(FILE, "<caret>aa")
        myFixture.performEditorAction(ACTION_REPLACE_REGEXP)

        val textField = ReplaceHandler.delegate!!.ui.textField
        val popup = ReplaceHandler.delegate!!.ui.popup

        textField.text = "a"
        popup.pressEnter(textField)

        textField.text = "b"
        popup.pressEnter(textField)

        popup.typeChar('!', textField)

        myFixture.checkResult("bb<caret>")
    }

    fun `test Mark is set when replace starts`() {
        MarkHandler.editorTypeId = ""
        myFixture.configureByText(FILE, "<caret>null () null () null")
        myFixture.performEditorAction(ACTION_REPLACE_TEXT)

        val textField = ReplaceHandler.delegate!!.ui.textField
        val popup = ReplaceHandler.delegate!!.ui.popup

        textField.text = "null"
        popup.pressEnter(textField)

        textField.text = "\"label\""
        popup.pressEnter(textField)

        popup.typeChar('y', textField)
        popup.typeChar('y', textField)
        popup.typeChar('n', textField)

        myFixture.checkResult(""""label" () "label" () null<caret>""")
        myFixture.performEditorAction(ACTION_POP_MARK)
        myFixture.checkResult("""<caret>"label" () "label" () null""")
    }

    private fun JBPopup.pressEnter(component: JComponent) {
        dispatchKeyEvent(KeyEvent(component, KeyEvent.KEY_PRESSED, 1234L, 0, VK_ENTER, CHAR_UNDEFINED))
        dispatchKeyEvent(KeyEvent(component, KeyEvent.KEY_RELEASED, 1234L, 0, VK_ENTER, CHAR_UNDEFINED))
    }

    private fun JBPopup.typeChar(char: Char, component: JComponent) {
        dispatchKeyEvent(KeyEvent(component, KeyEvent.KEY_TYPED, 1234L, 0, KeyEvent.VK_UNDEFINED, char))
    }
}
