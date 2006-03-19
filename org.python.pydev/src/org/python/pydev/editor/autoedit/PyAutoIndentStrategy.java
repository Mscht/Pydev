/*
 * Created on Dec 10, 2003
 * Author: atotic
 * License: Common Public License 1.0
 */

package org.python.pydev.editor.autoedit;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.DocumentCommand;
import org.eclipse.jface.text.IAutoEditStrategy;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.python.copiedfromeclipsesrc.PythonPairMatcher;
import org.python.pydev.core.Tuple;
import org.python.pydev.core.Tuple3;
import org.python.pydev.core.docutils.DocUtils;
import org.python.pydev.core.docutils.ParsingUtils;
import org.python.pydev.core.docutils.PySelection;
import org.python.pydev.editor.actions.PyAction;
import org.python.pydev.editor.codecompletion.PyCodeCompletion;

/**
 * Class which implements the following behaviors:
 * - indenting: after 'class' or 'def' 
 * - replacement: when typing colons or parentheses
 * 
 * This class uses the org.python.pydev.core.docutils.DocUtils class extensively
 * for some document-related operations.
 */
public class PyAutoIndentStrategy implements IAutoEditStrategy{

    private IIndentPrefs prefs;
    
    public PyAutoIndentStrategy(){
    }
    
    public void setIndentPrefs(IIndentPrefs prefs) {
        this.prefs = prefs;
    }

    public IIndentPrefs getIndentPrefs() {
        if (this.prefs == null) {
            this.prefs = new DefaultIndentPrefs(); //create the default if this is still not done.
        }
        return this.prefs;
    }

    
    
    /**
     * Set indentation automatically after newline.
     */
    private String autoIndentNewline(IDocument document, int length, String text, int offset)
            throws BadLocationException {
    	
        if (offset > 0) {
            PySelection selection = new PySelection(document, offset);
            String lineWithoutComments = PySelection.getLineWithoutComments(selection.getLine());
            int smartIndent = determineSmartIndent(document, offset, selection);
            
            if(lineWithoutComments.length() > 0){
                //ok, now let's see the auto-indent
                int curr = lineWithoutComments.length() -1;
                char lastChar = lineWithoutComments.charAt(curr);

                //we dont want whitespaces
                while (curr > 0 && Character.isWhitespace(lastChar)) {
                    curr--;
                    lastChar = lineWithoutComments.charAt(curr);
                }

                
                //we have to check if smartIndent is -1 because otherwise we are inside some bracket
                if(smartIndent == -1 && DocUtils.isClosingPeer(lastChar)){
                	//ok, not inside brackets
                    PythonPairMatcher matcher = new PythonPairMatcher(DocUtils.BRACKETS);
                    int bracketOffset = selection.getLineOffset()+curr;
                    IRegion region = matcher.match(document, bracketOffset+1);
                    
                    if(region != null){
                    	//we might not have a match if there is an error in the program...
                    	//e.g. a single ')' without its counterpart.
                        int openingBracketLine = document.getLineOfOffset(region.getOffset());
                        String openingBracketLineStr = PySelection.getLine(document, openingBracketLine);
                        int first = PySelection.getFirstCharPosition(openingBracketLineStr);
                        String initial = getCharsBeforeNewLine(text);
                        text = initial + openingBracketLineStr.substring(0, first);
                    }
                    return text;
                    
                } else if (smartIndent == -1 && lastChar == ':') {
                    //we have to check if smartIndent is -1 because otherwise we are in a dict
                	//ok, not inside brackets
                    text = indentBasedOnStartingScope(text, selection, false);
                    return text;
                }
            }
            
            String trimmedLine = lineWithoutComments.trim();
            
            //let's check for dedents...
            if(PySelection.startsWithDedentToken(trimmedLine)){
                text = dedent(text);
            }else if(smartIndent >= 0){
            	if(DocUtils.hasOpeningBracket(trimmedLine) || DocUtils.hasClosingBracket(trimmedLine)){
            		text = makeSmartIndent(text, smartIndent);
            	}
            }else{
            	if(selection.getLineContentsToCursor().trim().length() == 0){
            		text = indentBasedOnStartingScope(text, selection, true);
            	}
            }
        }
        return text;
    }

	private String indentBasedOnStartingScope(String text, PySelection selection, boolean checkForLowestBeforeNewScope) {
		Tuple3<String,Integer, String> previousIfLine = selection.getPreviousLineThatStartsScope();
		if(previousIfLine != null){
		    String initial = getCharsBeforeNewLine(text);
		    String indent = PySelection.getIndentationFromLine(previousIfLine.o1);
		    if(previousIfLine.o2 == -1){
                if(checkForLowestBeforeNewScope && previousIfLine.o3 != null){
                    indent = PySelection.getIndentationFromLine(previousIfLine.o3);
                    text = initial + indent;
                }else{
                    text = initial + indent + prefs.getIndentationString();
                }
		    }else{
		    	text = initial + indent;
		    }
		    
		}
		return text;
	}


	/**
	 * Returns the first offset greater than <code>offset</code> and smaller than
	 * <code>end</code> whose character is not a space or tab character. If no such
	 * offset is found, <code>end</code> is returned.
	 *
	 * @param document the document to search in
	 * @param offset the offset at which searching start
	 * @param end the offset at which searching stops
	 * @return the offset in the specified range whose character is not a space or tab
	 * @exception BadLocationException if position is an invalid range in the given document
	 */
	private int findEndOfWhiteSpace(IDocument document, int offset, int end) throws BadLocationException {
		while (offset < end) {
			char c= document.getChar(offset);
			if (c != ' ' && c != '\t') {
				return offset;
			}
			offset++;
		}
		return end;
	}

	private void autoIndentSameAsPrevious(IDocument d, DocumentCommand c) {
		String txt = autoIndentSameAsPrevious(d, c.offset, c.text);
		if(txt != null){
			c.text = txt;
		}
	}
	/**
	 * Copies the indentation of the previous line.
	 *
	 * @param d the document to work on
	 * @param text 
	 * @param c the command to deal with
	 */
	private String autoIndentSameAsPrevious(IDocument d, int offset, String text) {

		if (offset == -1 || d.getLength() == 0)
			return null;

		try {
			// find start of line
			int p= (offset == d.getLength() ? offset  - 1 : offset);
			IRegion info= d.getLineInformationOfOffset(p);
			int start= info.getOffset();

			// find white spaces
			int end= findEndOfWhiteSpace(d, start, offset);

			StringBuffer buf= new StringBuffer(text);
			if (end > start) {
				// append to input
				buf.append(d.get(start, end - start));
			}

			return buf.toString();

		} catch (BadLocationException excp) {
			// stop work
			return null;
		}
	}

    /**
     * @param document
     * @param length
     * @param text
     * @return
     */
    private boolean isNewLineText(IDocument document, int length, String text) {
        return length == 0 && text != null && AbstractIndentPrefs.endsWithNewline(document, text);
    }

    private String dedent(String text) {
        String indentationString = prefs.getIndentationString();
        int indentationLength = indentationString.length();
        int len = text.length();

        if(len >= indentationLength){
            text = text.substring(0, len - indentationLength);
        }
        return text;
    }
    private Tuple<String, Integer> removeFirstIndent(String text) {
        String indentationString = prefs.getIndentationString();
        if(text.startsWith(indentationString)){
            return new Tuple<String, Integer>(text.substring(indentationString.length()), indentationString.length());
        }
        return new Tuple<String, Integer>(text, 0);
    }

    /**
     * @see org.eclipse.jface.text.IAutoEditStrategy#customizeDocumentCommand(IDocument, DocumentCommand)
     */
    public void customizeDocumentCommand(IDocument document, DocumentCommand command) {
        // super idents newlines the same amount as the previous line
    	final boolean isNewLine = isNewLineText(document, command.length, command.text);
    	
    	if(isNewLine){
    		autoIndentSameAsPrevious(document, command);
    	}
        
        String contentType = ParsingUtils.getContentType(document.get(), command.offset);
		if(!contentType.equals( ParsingUtils.PY_DEFAULT)){
            //the indentation is only valid for things in the code (comments should not be indented).
            //(that is, if it is not a new line... in this case, it may have to be indented)
            if(!isNewLine){
            	//we have to take care about tabs anyway
            	getIndentPrefs().convertToStd(document, command);
                return;
            }
        }
        

        try {
        	
            if (isNewLine) {
            	if(prefs.getSmartIndentPar()){
	            	command.text = autoIndentNewline(document, command.length, command.text, command.offset);
	            	PySelection selection = new PySelection(document, command.offset);
	        		selection.deleteSpacesAfter(command.offset);
            	}
            }else if(command.text.equals("\t")){
            	PySelection ps = new PySelection(document, command.offset);
            	//it is a tab
            	String lineContentsToCursor = ps.getLineContentsToCursor();
            	int cursorLine = ps.getCursorLine();
            	if(cursorLine > 0){
            		//this is to know which would be expected if it was a new line in the previous line
            		//(so that we know the 'expected' output
            		IRegion prevLineInfo = document.getLineInformation(cursorLine-1);
            		int prevLineEndOffset = prevLineInfo.getOffset()+prevLineInfo.getLength();
            		String txt = autoIndentSameAsPrevious(document, prevLineEndOffset, "\n");
            		txt = autoIndentNewline(document, 0, txt, prevLineEndOffset);
            		txt = txt.substring(1);//remove the newline
            		
            		if (txt.length() > 0){
            			//now, we should not apply that indent if we are already at the 'max' indent in this line
            			//(or better: we should go to that max if it would pass it)
            			int sizeApplied = ps.getStartLineOffset()+ lineContentsToCursor.length() + txt.length();
            			int sizeExpected = ps.getStartLineOffset()+ txt.length();
            			int currSize = ps.getAbsoluteCursorOffset();

            			if(currSize >= sizeExpected){
            				//do nothing (we already passed what we expected from the indentation)
            			}else if(sizeExpected == sizeApplied){
            				ps.deleteSpacesAfter(command.offset);
            				command.text = txt;
            			}else if(sizeApplied > sizeExpected){
            				ps.deleteSpacesAfter(command.offset);
            				command.text = txt.substring(0, sizeExpected - currSize);
            			}
            		}
            	}
            }
            
            getIndentPrefs().convertToStd(document, command);

            
        	if (prefs.getAutoParentesis() && (command.text.equals("[") || command.text.equals("{")) ) {
        		PySelection ps = new PySelection(document, command.offset);
        		char c = command.text.charAt(0);
        		if (shouldClose(ps, c)) {
        			command.shiftsCaret = false;
                    command.text = c+""+DocUtils.getPeer(c);
                    command.caretOffset = command.offset+1;
        		}
        		
        	}else if (command.text.equals("(")) {
            	/*
            	 * Now, let's also check if we are in an 'elif ' that must be dedented in the doc
            	 */
            	autoDedentElif(document, command);

            	if(prefs.getAutoParentesis()){
            		PySelection ps = new PySelection(document, command.offset);
	                String line = ps.getLine();
	
	                if (shouldClose(ps, '(')) {
	
	                    boolean hasClass = line.indexOf("class ") != -1;
	                    boolean hasClassMethodDef = line.indexOf(" def ") != -1 || line.indexOf("\tdef ") != -1;
	                    boolean hasMethodDef = line.indexOf("def ") != -1;
	                    boolean hasNoDoublePoint = line.indexOf(":") == -1;
	
	                    command.shiftsCaret = false;
	                    if (hasNoDoublePoint && (hasClass || hasClassMethodDef || hasMethodDef)) {
	                        if (hasClass) {
	                            //command.text = "(object):"; //TODO: put some option in the interface for that
	                            //command.caretOffset = command.offset + 7;
	                            command.text = "():";
	                            command.caretOffset = command.offset + 1;
	                        } else if (hasClassMethodDef && prefs.getAutoAddSelf()) {
	                            command.text = "(self):";
	                            command.caretOffset = command.offset + 5;
	                        } else if (hasMethodDef) {
	                            command.text = "():";
	                            command.caretOffset = command.offset + 1;
	                        } else {
	                            throw new RuntimeException(getClass().toString() + ": customizeDocumentCommand()");
	                        }
	                    } else {
	                        command.text = "()";
	                        command.caretOffset = command.offset + 1;
	                    }
	                }
        		}

            }
	            
            else if (command.text.equals(":")) {
                /*
                 * The following code will auto-replace colons in function
                 * declaractions
                 * e.g.,
                 * def something(self):
                 *                    ^ cursor before the end colon
                 * 
                 * Typing another colon (i.e, ':') at that position will not insert
                 * another colon
                 */
                if(prefs.getAutoColon()){
                    performColonReplacement(document, command);
                }

                /*
                 * Now, let's also check if we are in an 'else:' that must be dedented in the doc
                 */
                autoDedentElse(document, command);
            }

            /*
             * this is a space... so, if we are in 'from xxx ', we may auto-write
             * the import 
             */
            else if (command.text.equals(" ")) {
            	if( prefs.getAutoWriteImport()){
            		PySelection ps = new PySelection(document, command.offset);
	                String completeLine = ps.getLineWithoutComments();
	                String lineToCursor = ps.getLineContentsToCursor().trim();
	                if(completeLine.indexOf("import") == -1){
	                    String importsTipperStr = PyCodeCompletion.getImportsTipperStr(lineToCursor, false);
	                    if(importsTipperStr.length() > 0){
	                        command.text = " import ";
	                    }
	                }
            	}
            	
            	
            	/*
            	 * Now, let's also check if we are in an 'elif ' that must be dedented in the doc
            	 */
            	autoDedentElif(document, command);
            }
            
            /*
             * If the command is some kind of parentheses or brace, and there's
             * already a matching one, don't insert it. Just move the cursor to
             * the next space.
             */
            else if (command.text.length() > 0 && prefs.getAutoBraces()) {
                // you can only do the replacement if the next character already there is what the user is trying to input
                
                if (command.offset < document.getLength() && document.get(command.offset, 1).equals(command.text)) {
                    // the following searches through each of the end braces and
                    // sees if the command has one of them

                    boolean found = false;
                    for (int i = 1; i <= DocUtils.BRACKETS.length && !found; i += 2) {
                        char c = DocUtils.BRACKETS[i];
                        if (c == command.text.charAt(0)) {
                            found = true;
                            performPairReplacement(document, command);
                        }
                    }
                }
            }
            

        }
        /*
         * If something goes wrong, you want to know about it, especially in a
         * unit test. If you don't rethrow the exception, unit tests will pass
         * even though you threw an exception.
         */
        catch (BadLocationException e) {
            // screw up command.text so unit tests can pick it up
            command.text = "BadLocationException";
            throw new RuntimeException(e);
        }
    }

    /**
     * This function makes the else auto-dedent (if available)
     * @return the new indent and the number of chars it has been dedented (so, that has to be considered as a shift to the left
     * on subsequent things).
     */
    public Tuple<String, Integer> autoDedentElse(IDocument document, DocumentCommand command, String tok) throws BadLocationException {
        if(getIndentPrefs().getAutoDedentElse()){
            PySelection ps = new PySelection(document, command.offset);
            String lineContents = ps.getCursorLineContents();
            if(lineContents.trim().equals(tok)){
                
                String previousIfLine = ps.getPreviousLineThatAcceptsElse();
                if(previousIfLine != null){
                    String ifIndent = PySelection.getIndentationFromLine(previousIfLine);
                    String lineIndent = PySelection.getIndentationFromLine(lineContents);
                    
                    String indent = prefs.getIndentationString();
                    if(lineIndent.length() == ifIndent.length()+indent.length()){
                        Tuple<String,Integer> dedented = removeFirstIndent(lineContents);
                        ps.replaceLineContentsToSelection(dedented.o1);
                        command.offset = command.offset - dedented.o2;
                        return dedented;
                    }
                }
            }        
        }
        return null;
    }
    
    public Tuple<String, Integer> autoDedentElse(IDocument document, DocumentCommand command) throws BadLocationException {
    	return autoDedentElse(document, command, "else");
    }

    /**
     * This function makes the else auto-dedent (if available)
     * @return the new indent and the number of chars it has been dedented (so, that has to be considered as a shift to the left
     * on subsequent things).
     */
    public Tuple<String, Integer> autoDedentElif(IDocument document, DocumentCommand command) throws BadLocationException {
    	return autoDedentElse(document, command, "elif");
    }
    

    /**
     * Create the indentation string after comma and a newline.
     * 
     * @param document
     * @param text
     * @param offset
     * @param selection 
     * @return Indentation String
     * @throws BadLocationException
     */
    private String makeSmartIndent(String text, int smartIndent)
            throws BadLocationException {
        if (smartIndent > 0) {
            String initial = text;

            // Discard everything but the newline from initial, since we'll
            // build the smart indent from scratch anyway.
            initial = getCharsBeforeNewLine(initial);

            // Create the actual indentation string
            String indentationString = prefs.getIndentationString();
            int indentationSteps = smartIndent / prefs.getTabWidth();
            int spaceSteps = smartIndent % prefs.getTabWidth();
            
            StringBuffer b = new StringBuffer(smartIndent);
            while (indentationSteps > 0){
                indentationSteps -= 1;
                b.append(indentationString);
            }
            
            if(prefs.getUseSpaces()){
                while (spaceSteps >= 0){
                    spaceSteps -= 1;
                    b.append(" ");
                }
            }

            return initial + b.toString();
        }
        return text;
    }

    /**
     * @param initial
     * @return
     */
    private String getCharsBeforeNewLine(String initial) {
        int initialLength = initial.length();
        for (int i = 0; i < initialLength; i++) {
            char theChar = initial.charAt(i);
            // This covers all cases I know of, but if there is any platform
            // with weird newline then this would need to be smarter.
            if (theChar != '\r' && theChar != '\n') {
                if (i > 0) {
                    initial = initial.substring(0, i);
                }
                break;
            }
        }
        return initial;
    }

    /**
     * Private function which is called when a colon is the command.
     * 
     * The following code will auto-replace colons in function declaractions
     * e.g., def something(self): ^ cursor before the end colon
     * 
     * Typing another colon (i.e, ':') at that position will not insert another
     * colon
     * 
     * @param document
     * @param command
     * @throws BadLocationException
     */
    private void performColonReplacement(IDocument document, DocumentCommand command) {
        PySelection ps = new PySelection(document, command.offset);
        int absoluteOffset = ps.getAbsoluteCursorOffset();
        int documentLength = ps.getDoc().getLength();

        // need to check whether whether we're at the very end of the document
        if (absoluteOffset < documentLength) {
            try {
                char currentCharacter = document.getChar(absoluteOffset);

                if (currentCharacter == ':') {
                    command.text = DocUtils.EMPTY_STRING;
                    command.caretOffset = command.offset + 1;
                }

            } catch (BadLocationException e) {
                // should never happen because I just checked the length 
                throw new RuntimeException(e);
            }

        }
    }

    /**
     * Private function to call to perform any replacement of braces.
     * 
     * The Eclipse Java editor does this by default, and it is very useful. If
     * you try to insert some kind of pair, be it a parenthesis or bracket in
     * Java, the character will not insert and instead the editor just puts your
     * cursor at the next position.
     * 
     * This function performs the equivalent for the Python editor.
     *  
     * @param document
     * @param command if the command does not contain a brace, this function does nothing.
     * @throws BadLocationException 
     */
    private void performPairReplacement(IDocument document, DocumentCommand command) throws BadLocationException {
        PySelection ps = new PySelection(document, command.offset);

        char c = ps.getCharAtCurrentOffset();
        char peer = DocUtils.getPeer(c);
        StringBuffer doc = new StringBuffer(document.get());
        //it is not enough just counting the chars, we have to ignore those that are within comments or literals.
        ParsingUtils.removeCommentsWhitespacesAndLiterals(doc);
        int chars = PyAction.countChars(c, doc);
        int peers = PyAction.countChars(peer, doc);
        
        if( chars == peers){
            //if we have the same number of peers, we want to eat the char
            command.text = DocUtils.EMPTY_STRING;
            command.caretOffset = command.offset + 1;
        }
    }

    /**
     * @param ps
     * @param c the char to close
     * @return
     * @throws BadLocationException
     */
    private boolean shouldClose(PySelection ps, char c) throws BadLocationException {
        String line = ps.getLine();

        String lineContentsFromCursor = ps.getLineContentsFromCursor();

        for (int i = 0; i < lineContentsFromCursor.length(); i++) {
            if (!Character.isWhitespace(lineContentsFromCursor.charAt(i))) {
                return false;
            }
        }

        int i = PyAction.countChars(c, line);
        int j = PyAction.countChars(c, line);

        if (j > i) {
            return false;
        }

        return true;
    }

    /**
	 * Return smart indent amount for new line. This should be done for
	 * multiline structures like function parameters, tuples, lists and
	 * dictionaries.
	 * 
	 * Example:
	 * 
	 * a=foo(1, #
	 * 
	 * We would return the indentation needed to place the caret at the #
	 * position.
	 * 
	 * @param document The document
	 * @param offset The document offset of the last character on the previous line
     * @param ps 
	 * @return indent, or -1 if smart indent could not be determined (fall back to default)
	 */
    private int determineSmartIndent(IDocument document, int offset, PySelection ps)
            throws BadLocationException {
    	
        PythonPairMatcher matcher = new PythonPairMatcher(DocUtils.BRACKETS);
        int openingPeerOffset = matcher.searchForAnyOpeningPeer(offset, document);
        if(openingPeerOffset == -1){
            return -1;
        }
        int openingPeerLineOffset = document.getLineInformationOfOffset(openingPeerOffset).getOffset();
        int len = openingPeerOffset - openingPeerLineOffset;
        String str = document.get(openingPeerLineOffset, len);
        for(int i = 0; i<str.length(); i++){
            if(str.charAt(i) == '\t'){
                len += prefs.getTabWidth() - 1;
            }
        }
        return len;
        
    }
}