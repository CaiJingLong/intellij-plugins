package com.intellij.grazie.ide.inspection.detection

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.grazie.GrazieBundle
import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.detection.DetectionContext
import com.intellij.grazie.detection.LangDetector
import com.intellij.grazie.detection.toLanguage
import com.intellij.grazie.grammar.ide.GraziePsiElementProcessor
import com.intellij.grazie.ide.inspection.detection.problem.LanguageDetectionProblemDescriptor
import com.intellij.grazie.ide.language.LanguageGrammarChecking
import com.intellij.grazie.ide.msg.GrazieStateLifecycle
import com.intellij.grazie.jlanguage.Lang
import com.intellij.grazie.utils.isInjectedFragment
import com.intellij.grazie.utils.lazyConfig
import com.intellij.openapi.util.KeyWithDefaultValue
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor

class LanguageDetectionInspection : LocalInspectionTool() {
  companion object : GrazieStateLifecycle {
    private val key = KeyWithDefaultValue.create("language-detection-inspection-key", DetectionContext.Local())

    private var enabledProgrammingLanguagesIDs: Set<String> by lazyConfig(this::init)
    private var available: Set<Lang> by lazyConfig(this::init)
    private var disabled: DetectionContext.State by lazyConfig(this::init)

    override fun init(state: GrazieConfig.State) {
      available = state.availableLanguages
      disabled = state.detectionContext
      enabledProgrammingLanguagesIDs = state.enabledProgrammingLanguages
    }

    override fun update(prevState: GrazieConfig.State, newState: GrazieConfig.State) {
      if (
        prevState.enabledProgrammingLanguages != newState.enabledProgrammingLanguages
        || prevState.availableLanguages != newState.availableLanguages
        || prevState.detectionContext != newState.detectionContext
      ) {
        init(newState)
      }
    }
  }

  override fun inspectionStarted(session: LocalInspectionToolSession, isOnTheFly: Boolean) {
    session.getUserData(key)!!.clear()
  }

  override fun inspectionFinished(session: LocalInspectionToolSession, problemsHolder: ProblemsHolder) {
    val context = session.getUserData(key)!!
    val languages = context.getToNotify((disabled.disabled + available.map { it.toLanguage() }).toSet())

    if (languages.isEmpty()) return

    problemsHolder.registerProblem(LanguageDetectionProblemDescriptor.create(id, problemsHolder.manager, session.file, languages))
  }

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
    return object : PsiElementVisitor() {
      override fun visitElement(element: PsiElement) {
        if (element.isInjectedFragment()) return

        if (element.language.id !in enabledProgrammingLanguagesIDs) return

        for (strategy in LanguageGrammarChecking.allForLanguageOrAny(element.language).filter { it.isMyContextRoot(element) }) {
          val (_, _, text) = GraziePsiElementProcessor.processElements(element, strategy)
          LangDetector.updateContext(text, session.getUserData(key)!!)
          break
        }
      }
    }
  }

  override fun getDisplayName() = GrazieBundle.message("grazie.detection.inspection.text")
}