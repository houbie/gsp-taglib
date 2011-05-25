/*
 * Copyright 2004-2005 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.codehaus.groovy.grails.web.pages;

import javax.swing.text.html.HTML;
import java.util.ArrayList;
import java.util.List;

/**
 * NOTE: Based on work done by on the GSP standalone project (https://gsp.dev.java.net/)
 *
 * Lexer for GroovyPagesServlet.
 *
 * @author Troy Heninger
 * @author Graeme Rocher
 *
 * Date: Jan 10, 2004
 */
class GroovyPageScanner_ implements Tokens_ {

    private String text;
    private int end1;
    private int begin1;
    private int end2;
    private int begin2;
    private int state = HTML;
    private int len;
    private int level;
    private boolean str1;
    private boolean str2;
    private String lastNamespace;
    private int exprBracketCount = 0;
    private List<Integer> lineNumberPositions;
    private int lastLineNumberIndex = -1;

    GroovyPageScanner_(String text) {
        Strip_ strip = new Strip_(text);
        strip.strip(0);
        this.text = strip.toString();
        len = this.text.length();
        resolveLineNumberPositions();
    }

    // add line starting positions to array
    private void resolveLineNumberPositions() {
        lineNumberPositions = new ArrayList<Integer>();
        // first line starts at 0
        lineNumberPositions.add(0);
        for (int i = 0; i < len; i++) {
            if (text.charAt(i) == '\n') {
                // next line starts after LF
                lineNumberPositions.add(i + 1);
            }
        }
    }

    private int found(int newState, int skip) {
        begin2 = begin1;
        end2 = --end1;
        begin1 = end1 += skip;
        int lastState = state;
        state = newState;
        return lastState;
    }

    private int foundStartOrEndTag(int newState, int skip, String namespace) {
        begin2 = begin1;
        end2 = --end1;
        begin1 = end1 += skip;
        int lastState = state;
        state = newState;
        lastNamespace = namespace;
        return lastState;
    }

    String getToken() {
        return text.substring(begin2, end2);
    }

    int getLineNumberForToken() {
        for (int i = lastLineNumberIndex + 1; i < lineNumberPositions.size(); i++) {
            if (lineNumberPositions.get(i) > begin2) {
                lastLineNumberIndex = i - 1;
                return i;
            }
        }
        // unknown
        return 1;
    }

    String getNamespace() {
        return lastNamespace;
    }

    int nextToken() {
        for (;;) {
            int left = len - end1;
            if (left == 0) {
                end1++; // in order to include the last letter
                return found(EOF, 0);
            }
            char c = text.charAt(end1++);
            char c1 = left > 1 ? text.charAt(end1) : 0;
            char c2 = left > 2 ? text.charAt(end1 + 1) : 0;
            if (str1) {
                if (c == '\\') end1++;
                else if (c == '\'') str1 = false;
                continue;
            }
            else if (str2) {
                if (c == '\\') end1++;
                else if (c == '"') str2 = false;
                continue;
            }
            else if (level > 0 && (c == ')' || c == '}' || c == ']')) {
                level--;
                continue;
            }

            switch (state) {
                case HTML:
                    if (c == '<' && left > 3) {
                        if (c1 == '%') {
                            if (c2 == '=') {
                                return found(JEXPR, 3);
                            }
                            if (c2 == '@') {
                                return found(JDIRECT, 3);
                            }
                            if (c2 == '!') {
                                return found(JDECLAR, 3);
                            }
                            if (c2 == '-' && left > 3 && text.charAt(end1 + 2) == '-') {
                                if (skipJComment()) continue;
                            }
                            return found(JSCRIPT, 2);
                        }

                        boolean bStartTag = true;
                        int fromIndex = end1; // we are expecting a start tag.
                        if (c1 == '/') {
                            bStartTag = false;
                            fromIndex = end1 + 1; // well it should be an end tag.
                        }

                        int foundColonIdx = text.indexOf(":", fromIndex);
                        if (foundColonIdx > -1) {
                            String tagNameSpace = text.substring(fromIndex,foundColonIdx);
                            if (tagNameSpace.matches("^\\p{Alpha}\\w*$")) {
                                if (bStartTag) {
                                    return foundStartOrEndTag(GSTART_TAG,tagNameSpace.length() + 2,tagNameSpace);
                                }

                                return foundStartOrEndTag(GEND_TAG,tagNameSpace.length() + 3,tagNameSpace);
                            }
                        }
                    }
                    else if (c == '$' && c1 == '{') {
                        return found(GEXPR, 2);
                    }

                    if (c == '%' && c1 == '{') {
                        if (c2 == '-' && left > 3 && text.charAt(end1 + 2) == '-') {
                            if (skipGComment()) continue;
                        }
                        return found(GSCRIPT, 2);
                    }

                    if (c == '!' && c1 == '{') {
                        return found(GDECLAR, 2);
                    }

                    if (c == '@' && c1 == '{') {
                        return found(GDIRECT, 2);
                    }

                    break;
                case JEXPR:
                case JSCRIPT:
                case JDIRECT:
                case JDECLAR:
                    if (c == '%' && c1 == '>') {
                        return found(HTML, 2);
                    }
                    break;
                case GSTART_TAG:
                    if (c == '$' && c1 == '{') {
                        return found(GTAG_EXPR, 2);
                    }
                    if (c == '>') {
                        return found(HTML,1);
                    }
                    else if (c == '/' && c1 == '>') {
                        return found(GEND_EMPTY_TAG,1);
                    }
                    break;
                case GEND_TAG:
                case GEND_EMPTY_TAG:
                    if (c == '>') {
                        return found(HTML,1);
                    }
                    break;
                case GTAG_EXPR:
                    if (c == '{') exprBracketCount++;
                    else if (c == '}') {
                        if (exprBracketCount>0) {
                            exprBracketCount--;
                        }
                        else {
                            return found(GSTART_TAG,1);
                        }
                    }
                    break;
                case GEXPR:
                case GDIRECT:
                    if (c == '}' && !str1 && !str2 && level == 0) {
                        return found(HTML, 1);
                    }
                    break;
                case GSCRIPT:
                    if (c == '}' && c1 == '%' && !str1 && !str2 && level == 0) {
                        return found(HTML, 2);
                    }
                    break;
                case GDECLAR:
                    if (c == '}' && (c1 == '!' || c1 == '%') && !str1 && !str2 && level == 0) {
                        return found(HTML, 2);
                    }
                    break;
            }
        }
    }

    private boolean skipComment(char c3, char c4) {
        int ix = end1 + 3;
        for (int ixz = len - 4; ; ix++) {
            if (ix >= ixz) return false;
            if (text.charAt(ix) == '-' && text.charAt(ix + 1) == '-' && text.charAt(ix + 2) == c3 &&
                    text.charAt(ix + 3) == c4) {
                break;
            }
        }
        text = text.substring(0, --end1) + text.substring(ix + 4);
        len = text.length();
        return true;
    }

    private boolean skipGComment() {
        return skipComment('}', '%');
    }

    private boolean skipJComment() {
        return skipComment('%', '>');
    }

    void reset() {
        end1 = begin1 = end2 = begin2 = level = 0;
        state = HTML;
        lastNamespace = null;
        lastLineNumberIndex = -1;
    }
}
