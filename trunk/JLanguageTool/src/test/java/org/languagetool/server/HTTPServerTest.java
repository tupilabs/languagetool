/* LanguageTool, a natural language style checker 
 * Copyright (C) 2006 Daniel Naber (http://www.danielnaber.de)
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
package org.languagetool.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.HashSet;

import javax.xml.parsers.ParserConfigurationException;

import org.junit.Ignore;
import org.junit.Test;
import org.languagetool.JLanguageTool;
import org.languagetool.Language;
import org.languagetool.XMLValidator;
import org.languagetool.tools.StringTools;
import org.xml.sax.SAXException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class HTTPServerTest {

  @Ignore("already gets tested by HTTPServerLoadTest")
  @Test
  public void testHTTPServer() throws Exception {
    final HTTPServer server = new HTTPServer();
    try {
      server.run();
      runTests();
    } finally {
      server.stop();
    }
  }

  void runTests() throws IOException, SAXException, ParserConfigurationException {
    // no error:
    final String matchAttr = "software=\"LanguageTool\" version=\"" + JLanguageTool.VERSION + "\" buildDate=\".*?\"";
    final String emptyResultPattern = "<\\?xml version=\"1.0\" encoding=\"UTF-8\"\\?>\n<matches " + matchAttr + ">\n</matches>\n";
    final String result1 = check(Language.GERMAN, "");
    assertTrue("Got " + result1 + ", expected " + emptyResultPattern, result1.matches(emptyResultPattern));
    final String result2 = check(Language.GERMAN, "Ein kleiner test");
    assertTrue("Got " + result2 + ", expected " + emptyResultPattern, result2.matches(emptyResultPattern));
    // one error:
    assertTrue(check(Language.GERMAN, "ein kleiner test").contains("UPPERCASE_SENTENCE_START"));
    // two errors:
    final String result = check(Language.GERMAN, "ein kleiner test. Und wieder Erwarten noch was: \u00f6\u00e4\u00fc\u00df.");
    assertTrue(result.contains("UPPERCASE_SENTENCE_START"));
    assertTrue(result.contains("WIEDER_WILLEN"));
    assertTrue("Expected special chars, got: '" + result+ "'",
            result.contains("\u00f6\u00e4\u00fc\u00df"));   // special chars are intact
    final XMLValidator validator = new XMLValidator();
    validator.validateXMLString(result, JLanguageTool.getDataBroker().getResourceDir() + "/api-output.dtd", "matches");
    validator.checkSimpleXMLString(result);
    //System.err.println(result);
    // make sure XML chars are escaped in the result to avoid invalid XML
    // and XSS attacks:
    assertTrue(!check(Language.GERMAN, "bla <script>").contains("<script>"));

    // other tests for special characters
    final String germanSpecialChars = check(Language.GERMAN, "ein kleiner test. Und wieder Erwarten noch was: öäüß+ öäüß.");
    assertTrue("Expected special chars, got: '" + germanSpecialChars + "'", germanSpecialChars.contains("öäüß+"));
    final String romanianSpecialChars = check(Language.ROMANIAN, "bla bla șțîâă șțîâă și câteva caractere speciale");
    assertTrue("Expected special chars, got: '" + romanianSpecialChars + "'", romanianSpecialChars.contains("șțîâă"));
    final String polishSpecialChars = check(Language.POLISH, "Mówiła długo, żeby tylko mówić mówić długo.");
    assertTrue("Expected special chars, got: '" + polishSpecialChars+ "'", polishSpecialChars.contains("mówić"));
    // test http POST
    assertTrue(checkByPOST(Language.ROMANIAN, "greșit greșit").contains("greșit"));
    // test supported language listing
    final URL url = new URL("http://localhost:" + HTTPServer.DEFAULT_PORT + "/Languages");
    final String languagesXML = StringTools.streamToString((InputStream) url.getContent(), "UTF-8");
    if (!languagesXML.contains("Romanian") || !languagesXML.contains("English")) {
      fail("Error getting supported languages: " + languagesXML);
    }
    if (!languagesXML.contains("abbr=\"de\"") || !languagesXML.contains("abbrWithVariant=\"de-DE\"")) {
      fail("Error getting supported languages: " + languagesXML);
    }
    // tests for "&" character
    assertTrue(check(Language.ENGLISH, "Me & you you").contains("&"));
    // tests for mother tongue (copy from link {@link FalseFriendRuleTest})   
    assertTrue(check(Language.ENGLISH, Language.GERMAN, "We will berate you").contains("BERATE"));
    assertTrue(check(Language.GERMAN, Language.ENGLISH, "Man sollte ihn nicht so beraten.").contains("BERATE"));
    assertTrue(check(Language.POLISH, Language.ENGLISH, "To jest frywolne.").contains("FRIVOLOUS"));
      
    //tests for bitext
    assertTrue(bitextCheck(Language.POLISH, Language.ENGLISH, "This is frivolous.", "To jest frywolne.").contains("FRIVOLOUS"));
    assertTrue(!bitextCheck(Language.POLISH, Language.ENGLISH, "This is something else.", "To jest frywolne.").contains("FRIVOLOUS"));
    
    //test for no changed if no options set
    String[] nothing = new String[0];
    assertEquals(check(Language.ENGLISH, Language.GERMAN, "We will berate you"), 
    		checkWithOptions(Language.ENGLISH, Language.GERMAN, "We will berate you", nothing, nothing));
    
    //disabling
    String[] disableAvsAn = new String[1];
    disableAvsAn[0] = "EN_A_VS_AN";
    assertTrue(!checkWithOptions(
    		Language.ENGLISH, Language.GERMAN, "This is an test", nothing, disableAvsAn).contains("an test"));

    //enabling
    assertTrue(checkWithOptions(
    		Language.ENGLISH, Language.GERMAN, "This is an test", disableAvsAn, nothing).contains("an test"));
    //should also mean disabling all other rules...
    assertTrue(!checkWithOptions(
    		Language.ENGLISH, Language.GERMAN, "We will berate you", disableAvsAn, nothing).contains("BERATE"));
    
    //test if two rules get enabled as well
    
    String[] twoRules = new String[2];
    twoRules[0] ="EN_A_VS_AN";
    twoRules[1] = "BERATE";
    
    String resultEn = checkWithOptions(
    		Language.ENGLISH, Language.GERMAN, "This is an test. We will berate you.", twoRules, nothing);
    
    assertTrue(resultEn.contains("EN_A_VS_AN"));
    assertTrue(resultEn.contains("BERATE"));

    //check two disabled options
    resultEn = checkWithOptions(
    		Language.ENGLISH, Language.GERMAN, "This is an test. We will berate you.", nothing, twoRules);
    
    assertTrue(!resultEn.contains("EN_A_VS_AN"));
    assertTrue(!resultEn.contains("BERATE"));
    
    //two disabled, one enabled, so enabled wins
    
    resultEn = checkWithOptions(
    		Language.ENGLISH, Language.GERMAN, "This is an test. We will berate you.", disableAvsAn, twoRules);

    assertTrue(resultEn.contains("EN_A_VS_AN"));
    assertTrue(!resultEn.contains("BERATE"));
    
  }

  @Test
  public void testAccessDenied() throws Exception {
    final HTTPServer server = new HTTPServer(HTTPServer.DEFAULT_PORT, false, false, new HashSet<String>());
    try {
      server.run();
      try {
        System.out.println("Testing 'access denied' check now");
        check(Language.GERMAN, "no ip address allowed, so this cannot work");
        fail();
      } catch (IOException expected) {
      }
    } finally {
      server.stop();
    }
  }
  
  private String check(Language lang, String text) throws IOException {
    return check(lang, null, text);
  }
  
  private String bitextCheck(Language lang, Language motherTongue, String sourceText, String text) throws IOException {
    String urlOptions = "/?language=" + lang.getShortName();
    urlOptions += "&srctext=" + URLEncoder.encode(sourceText, "UTF-8"); 
    urlOptions += "&text=" + URLEncoder.encode(text, "UTF-8"); // latin1 is not enough for languages like polish, romanian, etc
    if (null != motherTongue) {
      urlOptions += "&motherTongue="+motherTongue.getShortName();
    }
    final URL url = new URL("http://localhost:" + HTTPServer.DEFAULT_PORT + urlOptions);
    final InputStream stream = (InputStream)url.getContent();
    final String result = StringTools.streamToString(stream, "UTF-8");
    return result;
  }
  
  private String check(Language lang, Language motherTongue, String text) throws IOException {
    String urlOptions = "/?language=" + lang.getShortName();
    urlOptions += "&disabled=HUNSPELL_RULE&text=" + URLEncoder.encode(text, "UTF-8"); // latin1 is not enough for languages like polish, romanian, etc
    if (null != motherTongue) {
    	urlOptions += "&motherTongue=" + motherTongue.getShortName();
    }
    final URL url = new URL("http://localhost:" + HTTPServer.DEFAULT_PORT + urlOptions);
    final InputStream stream = (InputStream)url.getContent();
    final String result = StringTools.streamToString(stream, "UTF-8");
    return result;
  }
  
  private String checkWithOptions(Language lang, Language motherTongue, String text, 
		  String[] enabledRules, String[] disabledRules) throws IOException {
	  String urlOptions = "/?language=" + lang.getShortName();
	    urlOptions += "&text=" + URLEncoder.encode(text, "UTF-8"); // latin1 is not enough for languages like polish, romanian, etc
	    if (null != motherTongue) {
	    	urlOptions += "&motherTongue=" + motherTongue.getShortName();
	    }
	    	    
	    if (disabledRules.length > 0) { 	    
	    	urlOptions += "&disabled=" + join(disabledRules, ",");
	    }
	    if (enabledRules.length > 0) {
	    	urlOptions += "&enabled=" + join(enabledRules, ",");
	    }
	    
	    final URL url = new URL("http://localhost:" + HTTPServer.DEFAULT_PORT + urlOptions);
	    final InputStream stream = (InputStream)url.getContent();
	    final String result = StringTools.streamToString(stream, "UTF-8");
	    return result;
	  
  }
  
  /**
   * Same as {@link #check(Language, String)} but using HTTP POST method instead of GET
   */
  private String checkByPOST(Language lang, String text) throws IOException {
    final String postData = "language=" + lang.getShortName() + "&text=" + URLEncoder.encode(text, "UTF-8"); // latin1 is not enough for languages like polish, romanian, etc
    final URL url = new URL("http://localhost:" + HTTPServer.DEFAULT_PORT);
    final URLConnection connection = url.openConnection();
    connection.setDoOutput(true);
    final OutputStreamWriter wr = new OutputStreamWriter(connection.getOutputStream());
    try {
        wr.write(postData);
        wr.flush();
        final String result = StringTools.streamToString(connection.getInputStream(), "UTF-8");
        return result;
    } finally {
      wr.close();
    }
  }

  private static String join(String[] s, String delimiter) {
    if (s == null || s.length == 0 ) return "";
    final StringBuilder builder = new StringBuilder(s[0]);
    for (int i = 1; i < s.length; i++) {
      builder.append(delimiter).append(s[i]);
    }
    return builder.toString();
  }
  
}