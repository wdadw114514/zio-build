/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.render.text;

import dev.zio.client.modules.client.UIModule;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import net.minecraft.ChatFormatting;
import org.apache.commons.io.IOUtils;

public final class LanguageTable {
   private final Map<String, String> translations = new HashMap<>();
   private final Map<String, String> fragments = new LinkedHashMap<>();
   private volatile List<Map.Entry<String, String>> fragmentList = List.of();

   public LanguageTable() {
      InputStream languageInput = UIModule.LanguageChoice.CHINESE.openStream();
      this.load(languageInput);
   }

   public void load(InputStream input) {
      try {
         Charset encoding = StandardCharsets.UTF_8;
         parseInto(IOUtils.toString(input, encoding));
      } catch (Throwable ignoredFailure) {
      }
   }

   private void parseInto(String languageText) {
      this.translations.clear();
      this.fragments.clear();
      String sectionPrefix = "";
      boolean fragmentSection = false;
      for (String line : languageText.split("\n")) {
         line = line.trim();
         if (line.isEmpty()) {
            continue;
         }
         if (line.charAt(0) == '#') {
            continue;
         }
         if (line.charAt(0) == ';') {
            continue;
         }
         if (line.charAt(0) == '['
            && line.charAt(line.length() - 1) == ']'
            && line.length() > 2) {
            int sectionNameEnd = line.length() - 1;
            String sectionName = line.substring(1, sectionNameEnd);
            fragmentSection = "frag".equals(sectionName);
            sectionPrefix = sectionName + ".";
            continue;
         }

         int separatorIndex = line.indexOf('=');
         if (separatorIndex == -1) {
            continue;
         }
         String untrimmedKey = line.substring(0, separatorIndex);
         String trimmedKey = untrimmedKey.trim();
         if (trimmedKey.length() >= 2 && trimmedKey.startsWith("\"") && trimmedKey.endsWith("\"")) {
            trimmedKey = trimmedKey.substring(1, trimmedKey.length() - 1);
         }
         String key = (sectionPrefix + "\u0001")
            .replaceFirst("\u0001", Matcher.quoteReplacement(trimmedKey));
         String untrimmedValue = line.substring(separatorIndex + 1);
         String value = untrimmedValue.trim();
         if (value.length() >= 2
            && value.startsWith("\"")
            && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
         }
         value = value.replace("\\n", "\n");
         value = value.replace("<red>", ChatFormatting.RED.toString());
         value = value.replace("<yellow>", ChatFormatting.YELLOW.toString());
         if (fragmentSection) {
            if (!trimmedKey.isEmpty()) {
               this.fragments.put(trimmedKey, value);
            }
         } else {
            this.translations.put(key, value);
         }
      }
      List<Map.Entry<String, String>> ordered = new ArrayList<>(this.fragments.entrySet());
      ordered.sort(Comparator.comparingInt((Map.Entry<String, String> entry) -> entry.getKey().length()).reversed());
      this.fragmentList = ordered;
   }

   public String getTranslation(String key) {
      return this.translations.get(key);
   }

   public String getTranslationOrDefault(String key, String fallback) {
      return this.translations.getOrDefault(key, fallback);
   }

   /**
    * Looks up a plain text fragment (the {@code [text]} section of the language file).
    * Unknown fragments are returned unchanged so that dynamic values such as player
    * names or coordinates never get mangled.
    */
   public String translate(String text) {
      if (text == null || text.isEmpty()) {
         return text;
      }
      String translated = this.translations.get("text." + text);
      if (translated != null) {
         return translated;
      }
      String core = text.strip();
      if (core.isEmpty()) {
         return text;
      }
      translated = this.translations.get("text." + core);
      if (translated == null) {
         return text;
      }
      int start = text.indexOf(core);
      return text.substring(0, start) + translated + text.substring(start + core.length());
   }

   /**
    * Replaces every known phrase inside a composed message, which is how the client builds
    * sentences such as {@code "Macro " + name + " has been created"}.
    */
   public String translateFragments(String text) {
      if (text == null || text.length() < 3) {
         return text;
      }
      List<Map.Entry<String, String>> list = this.fragmentList;
      if (list.isEmpty()) {
         return text;
      }
      StringBuilder builder = null;
      int index = 0;
      int length = text.length();
      while (index < length) {
         Map.Entry<String, String> match = null;
         for (Map.Entry<String, String> entry : list) {
            if (text.startsWith(entry.getKey(), index)) {
               match = entry;
               break;
            }
         }
         if (match == null) {
            if (builder != null) {
               builder.append(text.charAt(index));
            }
            index++;
         } else {
            if (builder == null) {
               builder = new StringBuilder(length + 16);
               builder.append(text, 0, index);
            }
            builder.append(match.getValue());
            index += match.getKey().length();
         }
      }
      return builder == null ? text : builder.toString();
   }
}
