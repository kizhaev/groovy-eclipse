 /*
 * Copyright 2003-2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.codehaus.groovy.eclipse.codeassist.processors;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.eclipse.codeassist.CharArraySourceBuffer;
import org.codehaus.groovy.eclipse.codeassist.requestor.ContentAssistContext;
import org.codehaus.groovy.eclipse.codeassist.requestor.ContentAssistLocation;
import org.codehaus.groovy.eclipse.core.util.ExpressionFinder;
import org.codehaus.groovy.eclipse.core.util.ExpressionFinder.NameAndLocation;
import org.codehaus.jdt.groovy.model.GroovyCompilationUnit;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.internal.core.SearchableEnvironment;
import org.eclipse.jdt.ui.text.java.JavaContentAssistInvocationContext;
import org.eclipse.jface.text.contentassist.ICompletionProposal;

/**
 * @author Andrew Eisenberg
 * @created Nov 10, 2009
 *
 */
public class TypeCompletionProcessor extends AbstractGroovyCompletionProcessor {

    // GRECLIPSE-1527 field modifiers collection
    private static final Set<String> FIELD_MODIFIERS = new HashSet<String>();
    static {
        FIELD_MODIFIERS.add("private");
        FIELD_MODIFIERS.add("protected");
        FIELD_MODIFIERS.add("public");
        FIELD_MODIFIERS.add("static");
        FIELD_MODIFIERS.add("final");
    }
    // GRECLIPSE end

    public TypeCompletionProcessor(ContentAssistContext context,
            JavaContentAssistInvocationContext javaContext, SearchableEnvironment nameEnvironment) {
        super(context, javaContext, nameEnvironment);
    }

    public List<ICompletionProposal> generateProposals(IProgressMonitor monitor) {
        ContentAssistContext context = getContext();
        String toSearch = context.completionExpression.startsWith("new ") ? context.completionExpression.substring(4) : context.completionExpression;
        if (shouldShowTypes(context, toSearch)) {
            return Collections.emptyList();
        }

        int expressionStart = findExpressionStart(context);
        GroovyProposalTypeSearchRequestor requestor = new GroovyProposalTypeSearchRequestor(
                context, getJavaContext(), expressionStart,
                context.completionEnd - expressionStart,
                getNameEnvironment().nameLookup, monitor);

        getNameEnvironment().findTypes(toSearch.toCharArray(), true, // all member
                                                            // types, should
                                                            // be false when
                                                            // in
                                                            // constructor
                true, // camel case match
                getSearchFor(), requestor, monitor);

        List<ICompletionProposal> typeProposals = requestor
                .processAcceptedTypes();
        return typeProposals;
    }

    /**
     * Don't show types if there is no previous text (except if in imports)
     * Don't show types if there is a '.'
     * Don't show types when in a class body and there is a type declaration
     * immediately before
     *
     * @param context
     * @param toSearch
     * @return
     */
    private boolean shouldShowTypes(ContentAssistContext context,
            String toSearch) {
        return (toSearch.length() == 0 && context.location != ContentAssistLocation.IMPORT)
                || context.fullCompletionExpression.contains(".")
                || isBeforeTypeName(context.location, context.unit, context.completionLocation);
    }

    /**
     * @param context
     * @return
     */
    private int findExpressionStart(ContentAssistContext context) {
        // remove "new"
        int completionLength;
        if (context.completionExpression.startsWith("new ")) {
            completionLength = context.completionExpression.substring("new ".length()).trim().length();
        } else {
            completionLength = context.completionExpression.length();
        }

        int expressionStart = context.completionLocation-completionLength;
        return expressionStart;
    }

    /**
     * @return
     */
    private int getSearchFor() {
        switch(getContext().location) {
            case EXTENDS:
                return IJavaSearchConstants.CLASS;
            case IMPLEMENTS:
                return IJavaSearchConstants.INTERFACE;
            case EXCEPTIONS:
                return IJavaSearchConstants.CLASS;
            case ANNOTATION:
                return IJavaSearchConstants.ANNOTATION_TYPE;
            default:
                return IJavaSearchConstants.TYPE;
        }
    }

    private boolean isBeforeTypeName(ContentAssistLocation location, GroovyCompilationUnit unit, int completionLocation) {
        // GRECLIPE-1527 Updated logic
        if (location != ContentAssistLocation.CLASS_BODY) {
            return false;
        }
        NameAndLocation nameAndLocation = new ExpressionFinder().findPreviousTypeNameToken(
                new CharArraySourceBuffer(unit.getContents()), completionLocation);
        if (nameAndLocation == null) {
            return false;
        }
        if (!(getContext().completionNode instanceof FieldNode)) {
            return false;
        }
        return !FIELD_MODIFIERS.contains(nameAndLocation.name.trim());
        // GRECLIPSE end
    }

}
