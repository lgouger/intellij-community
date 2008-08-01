/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.impl.PsiMultiReference;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author peter
 */
public class LegacyCompletionContributor extends CompletionContributor {
  public static boolean DEBUG = false;

  public LegacyCompletionContributor() {
    final PsiElementPattern.Capture<PsiElement> everywhere = PlatformPatterns.psiElement();
    extend(CompletionType.BASIC, everywhere, new CompletionProvider<CompletionParameters>() {
      public void addCompletions(@NotNull final CompletionParameters parameters, final ProcessingContext matchingContext, @NotNull final CompletionResultSet _result) {
        final PsiFile file = parameters.getOriginalFile();
        final int offsetInFile = parameters.getOffset();
        final int startOffset = offsetInFile;
        final PsiElement lastElement = file.findElementAt(startOffset - 1);
        final PsiElement insertedElement = parameters.getPosition();
        CompletionData completionData = ApplicationManager.getApplication().runReadAction(new Computable<CompletionData>() {
          public CompletionData compute() {
            return CompletionUtil.getCompletionDataByElement(lastElement, file, startOffset);
          }
        });
        final CompletionResultSet result = _result.withPrefixMatcher(completionData == null
                                                                     ? CompletionData.findPrefixStatic(insertedElement, startOffset)
                                                                     : completionData.findPrefix(insertedElement, startOffset));
        if (completionData == null) {
          // some completion data may depend on prefix
          completionData = ApplicationManager.getApplication().runReadAction(new Computable<CompletionData>() {
            public CompletionData compute() {
              return CompletionUtil.getCompletionDataByElement(lastElement, file, startOffset);
            }
          });
        }

        if (completionData == null) return;

        final Set<LookupItem> lookupSet = new LinkedHashSet<LookupItem>();
        final PsiReference ref = ApplicationManager.getApplication().runReadAction(new Computable<PsiReference>() {
          public PsiReference compute() {
            return insertedElement.getContainingFile().findReferenceAt(offsetInFile);
          }
        });
        if (ref instanceof PsiMultiReference) {
          for (final PsiReference reference : completionData.getReferences((PsiMultiReference)ref)) {
            int offsetInElement = offsetInFile - reference.getElement().getTextRange().getStartOffset();
            final CompletionResultSet resultSet = result.withPrefixMatcher(
                reference.getElement().getText().substring(reference.getRangeInElement().getStartOffset(), offsetInElement));
            completionData.completeReference(reference, lookupSet, insertedElement, parameters.getOriginalFile(), offsetInFile);
            for (final LookupItem item : lookupSet) {
              resultSet.addElement(item);
            }
            lookupSet.clear();
          }
        }
        else if (ref != null) {
          completionData.completeReference(ref, lookupSet, insertedElement, parameters.getOriginalFile(),
                                           offsetInFile);
        }
        for (final LookupItem item : lookupSet) {
          result.addElement(item);
        }
        lookupSet.clear();

        final Set<CompletionVariant> keywordVariants = new HashSet<CompletionVariant>();
        completionData.addKeywordVariants(keywordVariants, insertedElement, parameters.getOriginalFile());
        completionData.completeKeywordsBySet(lookupSet, keywordVariants, insertedElement, result.getPrefixMatcher(), parameters.getOriginalFile());
        for (final LookupItem item : lookupSet) {
          result.addElement(item);
        }
      }
    });


  }


}
