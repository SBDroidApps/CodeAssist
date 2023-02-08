package com.tyron.completion.lookup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tyron.completion.EditorMemory;
import com.tyron.completion.InsertionContext;
import com.tyron.completion.model.CompletionItemWithMatchLevel;
import com.tyron.editor.Editor;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.com.intellij.openapi.util.Key;
import org.jetbrains.kotlin.com.intellij.openapi.util.UserDataHolder;
import org.jetbrains.kotlin.com.intellij.openapi.util.UserDataHolderBase;
import org.jetbrains.kotlin.com.intellij.psi.PsiElement;
import org.jetbrains.kotlin.com.intellij.psi.PsiFile;
import org.jetbrains.kotlin.com.intellij.psi.ResolveResult;
import org.jetbrains.kotlin.com.intellij.psi.SmartPsiElementPointer;

import java.util.Collections;
import java.util.Set;

import io.github.rosemoe.sora.lang.completion.CompletionItem;
import io.github.rosemoe.sora.text.CharPosition;
import io.github.rosemoe.sora.text.Content;
import io.github.rosemoe.sora.widget.CodeEditor;

public abstract class LookupElement extends CompletionItemWithMatchLevel implements UserDataHolder {

    private final UserDataHolderBase userDataHolderBase = new UserDataHolderBase();

    public LookupElement() {
        super("");
    }

    @Override
    public <T> @org.jetbrains.annotations.Nullable T getUserData(@NotNull Key<T> key) {
        return userDataHolderBase.getUserData(key);
    }

    @Override
    public <T> void putUserData(@NotNull Key<T> key, @org.jetbrains.annotations.Nullable T t) {
        userDataHolderBase.putUserData(key, t);
    }

    public static final LookupElement[] EMPTY_ARRAY = new LookupElement[0];

    /**
     * @return the string which will be inserted into the editor when this lookup element is chosen
     */
    @NonNull
    public abstract String getLookupString();

    /**
     * @return a set of strings which will be matched against the prefix typed by the user.
     * If none of them match, this item won't be suggested to the user.
     * The returned set must contain {@link #getLookupString()}.
     * @see #isCaseSensitive()
     */
    public Set<String> getAllLookupStrings() {
        return Collections.singleton(getLookupString());
    }

    /**
     * @return some object that this lookup element represents, often a {@link PsiElement} or another kind of symbol.
     * This is mostly used by extensions analyzing the lookup elements, e.g. for sorting purposes.
     */
    @NonNull
    public Object getObject() {
        return this;
    }

    /**
     * @return a PSI element associated with this lookup element. It's used for navigation, showing quick documentation and sorting by proximity to the current location.
     * The default implementation tries to extract PSI element from {@link #getObject()} result.
     */
    @Nullable
    public PsiElement getPsiElement() {
        Object o = getObject();
        if (o instanceof PsiElement) {
            return (PsiElement)o;
        }
        if (o instanceof ResolveResult) {
            return ((ResolveResult)o).getElement();
        }
//        if (o instanceof PsiElementNavigationItem) {
//            return ((PsiElementNavigationItem)o).getTargetElement();
//        }
        if (o instanceof SmartPsiElementPointer) {
            return ((SmartPsiElementPointer<?>)o).getElement();
        }
        return null;
    }

    /**
     * @return whether this lookup element is still valid (can be rendered, inserted, queried for {@link #getObject()}.
     * A lookup element may become invalidated if e.g. its underlying PSI becomes invalidated.
     * @see PsiElement#isValid()
     */
    public boolean isValid() {
        final Object object = getObject();
        if (object instanceof PsiElement) {
            return ((PsiElement)object).isValid();
        }
        return true;
    }


    @Override
    public final void performCompletion(@NonNull CodeEditor editor,
                                  @NonNull Content text,
                                  @NonNull CharPosition position) {
        super.performCompletion(editor, text, position);
    }

    @Override
    public final void performCompletion(@NonNull CodeEditor editor,
                                  @NonNull Content text,
                                  int line,
                                  int column) {
        PsiFile file = EditorMemory.getUserData(editor, EditorMemory.FILE_KEY);
        String prefix = EditorMemory.getUserData(editor, EditorMemory.PREFIX_KEY);

        InsertionContext context = new InsertionContext(editor, file);

        if (prefix != null && !prefix.isEmpty()) {
            for (int i = 0; i < prefix.length(); i++) {
                editor.deleteText();
            }
        }

        String lookupString = getLookupString();
        editor.insertText(lookupString, lookupString.length());

        handleInsert(context);
    }

    public void handleInsert(InsertionContext context) {

    }

    @NonNull
    @Override
    public String toString() {
        return getLookupString();
    }

    /**
     * Return the first element of the given class in a {@link LookupElementDecorator} wrapper chain.
     * If this object is not a decorator, return it if it's instance of the given class, otherwise null.
     */
    @Nullable
    public <T> T as(@NonNull Class<T> clazz) {
        //noinspection unchecked
        return clazz.isInstance(this) ? (T) this : null;
    }

    /**
     * @return whether prefix matching should be done case-sensitively for this lookup element
     * @see #getAllLookupStrings()
     */
    public boolean isCaseSensitive() {
        return true;
    }

    public void renderElement(LookupElementPresentation presentation) {
        presentation.setItemText(getLookupString());
    }

    /**
     * @return a renderer (if any) that performs potentially expensive computations on this lookup element.
     * It's called on a background thread, not blocking this element from being shown to the user.
     * It may return this lookup element's presentation appended with more details than {@link #renderElement} has given.
     * If the {@link Lookup} is already shown, it will be repainted/resized to accommodate the changes.
     */
    public @Nullable LookupElementRenderer<? extends LookupElement> getExpensiveRenderer() {
        return null;
    }
}
