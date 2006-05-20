/*
 * Created on May 20, 2006
 */
package com.python.pydev.refactoring.wizards;

import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.python.pydev.editor.codecompletion.revisited.visitors.AssignDefinition;
import org.python.pydev.editor.codecompletion.revisited.visitors.Definition;
import org.python.pydev.editor.refactoring.RefactoringRequest;

import com.python.pydev.analysis.scopeanalysis.ScopeAnalysis;

public class PyRenameAttributeProcess extends AbstractRenameRefactorProcess{

    private AssignDefinition assignDefinition;

    public PyRenameAttributeProcess(Definition definition) {
        super(definition);
        this.assignDefinition = (AssignDefinition) definition;
    }


    protected void checkInitialOnLocalScope(RefactoringStatus status, RefactoringRequest request) {
        addOccurrences(request, ScopeAnalysis.getAttributeOcurrences(this.assignDefinition.target, request.getAST()));
    }

}
