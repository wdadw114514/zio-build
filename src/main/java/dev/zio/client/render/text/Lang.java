/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.render.text;

import dev.zio.client.ClientContext;
import dev.zio.client.managers.ModuleRegistry;
import dev.zio.client.settings.NamedEntry;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.PlainTextContents;

/**
 * Small helper around {@link LanguageTable} used by every rendering path so that
 * plain English literals coming from the decompiled sources can be localized
 * without touching thousands of call sites.
 */
public final class Lang {
   private static final Map<String, String> CACHE = new ConcurrentHashMap<>();
   private static final int CACHE_LIMIT = 8192;
   private static Set<String> moduleNames;
   private static int moduleNameCount = -1;

   private Lang() {
   }

   /** Module names are user facing identifiers and must never be translated. */
   private static boolean isModuleName(String text) {
      ModuleRegistry registry = ClientContext.moduleRegistry;
      if (registry == null) {
         return false;
      }
      Collection<?> entries = registry.getRegistry();
      if (entries == null) {
         return false;
      }
      if (moduleNames == null || entries.size() != moduleNameCount) {
         Set<String> names = new HashSet<>(Math.max(16, entries.size()));
         for (Object entry : entries) {
            if (entry instanceof NamedEntry named) {
               names.add(named.getName());
            }
         }
         moduleNames = names;
         moduleNameCount = entries.size();
      }
      return moduleNames.contains(text);
   }

   private static String lookup(String text) {
      LanguageTable table = ClientContext.languageTable;
      if (table == null) {
         return text;
      }
      String translated = table.translate(text);
      if (translated == text || translated.equals(text)) {
         return text;
      }
      if (isModuleName(text)) {
         return text;
      }
      return translated;
   }

   /** Translates a raw string, returning it untouched when no entry exists. */
   public static String tr(String text) {
      if (text == null || text.isEmpty()) {
         return text;
      }
      String cached = CACHE.get(text);
      if (cached != null) {
         return cached;
      }
      String result = lookup(text);
      if (CACHE.size() < CACHE_LIMIT) {
         CACHE.put(text, result);
      }
      return result;
   }

   /** Same as {@link #tr(String)} but also translates phrases inside composed chat messages. */
   public static String trChat(String text) {
      if (text == null || text.isEmpty()) {
         return text;
      }
      LanguageTable table = ClientContext.languageTable;
      if (table == null) {
         return text;
      }
      if (isModuleName(text)) {
         return text;
      }
      String exact = table.translate(text);
      if (!exact.equals(text)) {
         return exact;
      }
      return table.translateFragments(text);
   }

   private static MutableComponent translate(Component component, boolean chat) {
      if (component == null) {
         return null;
      }
      MutableComponent result;
      if (component.getContents() instanceof PlainTextContents plain) {
         String text = chat ? trChat(plain.text()) : tr(plain.text());
         result = Component.literal(text).withStyle(component.getStyle());
      } else {
         result = MutableComponent.create(component.getContents()).withStyle(component.getStyle());
      }
      for (Component sibling : component.getSiblings()) {
         result.append(translate(sibling, chat));
      }
      return result;
   }

   /** Translates a component tree leaf by leaf, keeping styles and non-literal contents intact. */
   public static MutableComponent translate(Component component) {
      return translate(component, false);
   }

   /** Component variant that also runs phrase replacement, used for chat output. */
   public static MutableComponent translateChat(Component component) {
      return translate(component, true);
   }

   /** Convenience factory mirroring {@link Component#literal(String)} with localization. */
   public static MutableComponent literal(String text) {
      return Component.literal(tr(text));
   }
}
