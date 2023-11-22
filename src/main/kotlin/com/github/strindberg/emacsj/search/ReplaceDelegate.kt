package com.github.strindberg.emacsj.search

import java.awt.event.KeyEvent
import java.awt.event.KeyEvent.VK_ENTER
import java.lang.invoke.MethodHandles
import java.util.*
import com.github.strindberg.emacsj.search.ReplaceHandler.Companion.addPrevious
import com.github.strindberg.emacsj.search.SearchType.REGEXP
import com.github.strindberg.emacsj.word.substring
import com.github.strindberg.emacsj.word.text
import com.intellij.find.FindManager
import com.intellij.find.FindModel
import com.intellij.find.FindResult
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.colors.EditorColors.IDENTIFIER_UNDER_CARET_ATTRIBUTES
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.editor.markup.TextAttributes.ERASE_MARKER
import com.intellij.ui.JBColor
import org.jetbrains.annotations.VisibleForTesting

internal class ReplaceDelegate(val editor: Editor, val type: SearchType, val selection: IntRange?, lastSearch: Replace?) {

    @Suppress("unused")
    private val logger = Logger.getInstance(MethodHandles.lookup().lookupClass())

    private val caretListener = object : CaretListener {
        override fun caretAdded(e: CaretEvent) {
            ui.popup.cancel()
        }
    }

    private var searchArg: String = ""

    private var replaceArg: String = ""

    private lateinit var replaceModel: FindModel

    private lateinit var lastResult: FindResult

    private lateinit var undoGroupId: String

    private var replaced = 0

    private val identifierAttributes: TextAttributes

    internal var state: ReplaceState = ReplaceState.GET_SEARCH_ARG
        set(state) {
            field = state
            ui.title = getReplaceTitle()
        }

    internal var text: String
        get() = ui.text
        set(newText) {
            ui.text = newText
        }

    @VisibleForTesting
    internal val ui = CommonUI(editor, ::keyEventHandler, ::hide, true)

    init {
        lastSearch?.let { (search, replace) ->
            searchArg = search
            replaceArg = replace
            ui.text = getReplaceChoiceText()
            ui.selectText()
        }

        ui.title = getReplaceTitle()

        editor.caretModel.removeSecondaryCarets()
        editor.caretModel.addCaretListener(caretListener)

        identifierAttributes = editor.colorsScheme.getAttributes(IDENTIFIER_UNDER_CARET_ATTRIBUTES)
        editor.colorsScheme.setAttributes(IDENTIFIER_UNDER_CARET_ATTRIBUTES, ERASE_MARKER)

        ui.show()
    }

    internal fun hide(): Boolean {
        editor.markupModel.removeAllHighlighters()

        editor.caretModel.removeCaretListener(caretListener)

        editor.colorsScheme.setAttributes(IDENTIFIER_UNDER_CARET_ATTRIBUTES, identifierAttributes)

        ReplaceHandler.delegate = null

        return true
    }

    private fun keyEventHandler(e: KeyEvent): Boolean {
        when (state) {
            ReplaceState.GET_SEARCH_ARG -> {
                if (e.keyCode == VK_ENTER && e.id == KeyEvent.KEY_RELEASED && e.modifiersEx == 0) {
                    editor.markupModel.removeAllHighlighters()
                    val matchResult = Regex("(^.*) -> (.*)$", RegexOption.DOT_MATCHES_ALL).matchEntire(ui.text)?.destructured
                    if (matchResult != null) {
                        searchArg = matchResult.component1()
                        replaceArg = matchResult.component2()
                        startSearch()
                    } else {
                        state = ReplaceState.GET_REPLACE_ARG
                        searchArg = ui.text
                        ui.text = ""
                        ReplaceHandler.resetPos()
                    }
                } else if (e.id == KeyEvent.KEY_RELEASED) {
                    findAllAndHighlight(editor, ui.text, type, type == REGEXP || caseSensitive(ui.text), selection)
                }
            }

            ReplaceState.GET_REPLACE_ARG -> {
                if (e.keyCode == VK_ENTER && e.id == KeyEvent.KEY_RELEASED) {
                    replaceArg = ui.text
                    startSearch()
                }
            }

            ReplaceState.SEARCHING -> {
            }

            ReplaceState.SEARCH_FOUND -> {
                if (e.id == KeyEvent.KEY_TYPED) {
                    when (e.keyChar.lowercaseChar()) {
                        'y', ' ' -> {
                            try {
                                replaceInEditor()
                                searchForReplacement(true)
                            } catch (e: FindManager.MalformedReplacementStringException) {
                                handleReplacementError()
                            }
                        }

                        'n' -> {
                            searchForReplacement(true)
                        }

                        '.' -> {
                            try {
                                replaceInEditor()
                            } catch (e: FindManager.MalformedReplacementStringException) {
                                handleReplacementError()
                            }
                            ui.popup.cancel()
                        }

                        '!' -> {
                            try {
                                do {
                                    replaceInEditor()
                                    searchForReplacement(false)
                                } while (lastResult.isStringFound)
                            } catch (e: FindManager.MalformedReplacementStringException) {
                                handleReplacementError()
                            }
                            editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
                        }

                        else -> {
                            ui.popup.cancel()
                        }
                    }
                }
            }

            ReplaceState.REPLACE_DONE, ReplaceState.REPLACE_FAILED -> {
                if (e.id == KeyEvent.KEY_PRESSED) {
                    ui.popup.cancel()
                }
            }
        }
        return false
    }

    private fun startSearch() {
        state = ReplaceState.SEARCHING

        addPrevious(searchArg, replaceArg, type)

        ui.makeReadonly(getReplaceChoiceText())
        setupModel()

        editor.selectionModel.removeSelection()

        searchForReplacement(true)
    }

    private fun setupModel() {
        replaceModel = FindModel().apply {
            stringToFind = searchArg
            isForward = true
            isRegularExpressions = type == REGEXP
            isCaseSensitive = type == REGEXP || caseSensitive(searchArg) || caseSensitive(replaceArg)
            isPreserveCase = !(type == REGEXP || caseSensitive(searchArg) || caseSensitive(replaceArg))
            stringToReplace = fixBackReferences(replaceArg)
        }
        undoGroupId = UUID.randomUUID().toString()
    }

    private fun fixBackReferences(replaceArg: String) = replaceArg.replace(Regex("\\\\([1-9])"), "\\$$1").replace(Regex("\\\\&"), "\\$0")

    private fun handleReplacementError() {
        ui.textColor = JBColor.RED
        editor.markupModel.removeAllHighlighters()
        state = ReplaceState.REPLACE_FAILED
    }

    private fun getReplaceTitle() = when (state) {
        ReplaceState.GET_SEARCH_ARG -> if (type == REGEXP) "Query replace regexp: " else "Query replace: "
        ReplaceState.GET_REPLACE_ARG, ReplaceState.SEARCHING -> "Replace with: "
        ReplaceState.SEARCH_FOUND -> "Replace? "
        ReplaceState.REPLACE_DONE -> if (replaced == 1) "Replaced 1 occurrence." else "Replaced $replaced occurrences."
        ReplaceState.REPLACE_FAILED -> "Replacement failed. "
    }

    private fun getReplaceChoiceText(): String = "$searchArg -> $replaceArg"

    private fun searchForReplacement(highlight: Boolean) {
        val result = if (selection != null) {
            FindManager.getInstance(editor.project).findString(editor.text.substring(0, selection.last), selection.first, replaceModel)
        } else {
            FindManager.getInstance(editor.project).findString(editor.text, editor.caretModel.offset, replaceModel)
        }

        if (result.isStringFound) {
            editor.caretModel.moveToOffset(result.endOffset)

            if (highlight) {
                findAllAndHighlight(editor, searchArg, type, replaceModel.isCaseSensitive, selection)
                editor.markupModel.addRangeHighlighter(
                    EMACSJ_PRIMARY,
                    result.startOffset,
                    result.endOffset,
                    HighlighterLayer.LAST + 2,
                    HighlighterTargetArea.EXACT_RANGE
                )
                editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
            }

            state = ReplaceState.SEARCH_FOUND
        } else {
            ui.text = ""
            state = ReplaceState.REPLACE_DONE
        }

        lastResult = result
    }

    private fun replaceInEditor() {
        val foundString = editor.document.substring(lastResult.startOffset, lastResult.endOffset)
        val replacement = FindManager.getInstance(editor.project)
            .getStringToReplace(foundString, replaceModel, lastResult.startOffset, editor.text)

        WriteCommandAction.runWriteCommandAction(editor.project, "Replace ${type.name.lowercase()}", undoGroupId, {
            editor.document.replaceString(lastResult.startOffset, lastResult.endOffset, replacement)
        })

        editor.caretModel.moveToOffset(lastResult.startOffset + replacement.length)

        replaced++
    }
}

internal enum class ReplaceState { GET_SEARCH_ARG, GET_REPLACE_ARG, SEARCHING, SEARCH_FOUND, REPLACE_DONE, REPLACE_FAILED }

internal data class Replace(val search: String, val replace: String) {
    companion object {
        val EMPTY = Replace("", "")
    }
}
