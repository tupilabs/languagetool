/* JLanguageTool, a natural language style checker 
 * Copyright (C) 2005 Daniel Naber (http://www.danielnaber.de)
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301
 * USA
 */
package de.danielnaber.languagetool;

import java.util.Iterator;
import java.util.List;

import de.danielnaber.languagetool.tokenizers.WordTokenizer;

/**
 * @author Daniel Naber
 */
public class TestTools {
  
  private TestTools() {}

  public static AnalyzedSentence getAnaylzedText(String sentence) {
    WordTokenizer wtokenizer = new WordTokenizer();
    List tokens = wtokenizer.tokenize(sentence);
    AnalyzedToken[] tokensArray = new AnalyzedToken[tokens.size()];
    int i = 0;
    int charPos = 0;
    for (Iterator iter = tokens.iterator(); iter.hasNext();) {
      String token = (String) iter.next();
      tokensArray[i] = new AnalyzedToken(token, "FIXME", charPos);
      charPos += token.length();
      i++;
    }
    AnalyzedSentence text = new AnalyzedSentence(tokensArray);
    return text;
  }

}
