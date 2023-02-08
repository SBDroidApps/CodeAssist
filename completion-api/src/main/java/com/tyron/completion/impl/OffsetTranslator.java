package com.tyron.completion.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.com.intellij.openapi.Disposable;
import org.jetbrains.kotlin.com.intellij.openapi.editor.Document;
import org.jetbrains.kotlin.com.intellij.openapi.editor.event.DocumentEvent;
import org.jetbrains.kotlin.com.intellij.openapi.editor.event.DocumentListener;
import org.jetbrains.kotlin.com.intellij.openapi.editor.impl.event.DocumentEventImpl;
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer;
import org.jetbrains.kotlin.com.intellij.openapi.util.Key;
import org.jetbrains.kotlin.com.intellij.psi.PsiFile;
import org.jetbrains.kotlin.com.intellij.psi.util.PsiModificationTracker;

import java.util.ArrayList;
import java.util.List;

public class OffsetTranslator implements Disposable {
  static final Key<OffsetTranslator> RANGE_TRANSLATION = Key.create("completion.rangeTranslation");

  private final PsiFile myOriginalFile;
  private final Document myCopyDocument;
  private final List<DocumentEvent> myTranslation = new ArrayList<>();

  public OffsetTranslator(Document originalDocument, PsiFile originalFile, Document copyDocument, int start, int end, String replacement) {
    myOriginalFile = originalFile;
    myCopyDocument = copyDocument;
    myCopyDocument.putUserData(RANGE_TRANSLATION, this);
    myTranslation.add(new DocumentEventImpl(copyDocument, start, originalDocument.getImmutableCharSequence().subSequence(start, end),
                                            replacement, 0, false, start, end-start, start));
    Disposer.register(originalFile.getProject(), this);

    List<DocumentEvent> sinceCommit = new ArrayList<>();
    originalDocument.addDocumentListener(new DocumentListener() {
      @Override
      public void documentChanged(@NotNull DocumentEvent e) {
        if (isUpToDate()) {
          DocumentEventImpl inverse =
            new DocumentEventImpl(originalDocument, e.getOffset(), e.getNewFragment(), e.getOldFragment(), 0, false, e.getOffset(), e.getNewFragment().length(), e.getOffset());
          sinceCommit.add(inverse);
        }
      }
    }, this);

    originalFile.getProject().getMessageBus().connect(this).subscribe(PsiModificationTracker.TOPIC, new PsiModificationTracker.Listener() {
      final long lastModCount = originalFile.getViewProvider().getModificationStamp();
      @Override
      public void modificationCountChanged() {
        if (isUpToDate() && lastModCount != originalFile.getViewProvider().getModificationStamp()) {
          myTranslation.addAll(sinceCommit);
          sinceCommit.clear();
        }
      }
    });

  }

  private boolean isUpToDate() {
    return this == myCopyDocument.getUserData(RANGE_TRANSLATION) && myOriginalFile.isValid();
  }

  @Override
  public void dispose() {
    if (isUpToDate()) {
      myCopyDocument.putUserData(RANGE_TRANSLATION, null);
    }
  }

  @Nullable
  Integer translateOffset(Integer offset) {
    for (DocumentEvent event : myTranslation) {
      offset = translateOffset(offset, event);
      if (offset == null) {
        return null;
      }
    }
    return offset;
  }

  @Nullable
  private static Integer translateOffset(int offset, DocumentEvent event) {
    if (event.getOffset() < offset && offset < event.getOffset() + event.getNewLength()) {
      if (event.getOldLength() == 0) {
        return event.getOffset();
      }

      return null;
    }

    return offset <= event.getOffset() ? offset : offset - event.getNewLength() + event.getOldLength();
  }

}