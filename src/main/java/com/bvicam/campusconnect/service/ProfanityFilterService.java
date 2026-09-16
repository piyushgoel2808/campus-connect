package com.bvicam.campusconnect.service;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

@Service
public class ProfanityFilterService {

    // Comprehensive list of abusive/profane words (English and Hindi/Hinglish)
    private static final Set<String> PROFANITY_WORDS = new HashSet<>(Arrays.asList(
            // English profanities
            "fuck", "fucker", "fucking", "fucked", "fuckup",
            "shit", "shitty", "bullshit", "dipshit",
            "asshole", "dumbass", "jackass", "badass",
            "bitch", "bitching", "bitches",
            "bastard", "cunt", "dick", "dickhead", "cock", "cocksucker",
            "pussy", "fag", "faggot", "slut", "whore",
            "motherfucker", "motherfucking", "retard", "retarded",
            "douche", "douchebag", "prick", "twat", "wanker", "bollocks",
            "blowjob", "nigger", "nigga",

            // Hindi / Hinglish abusive terms
            "bhenchod", "behenchod", "behenchode", "bhanchod", "bhenchode",
            "madarchod", "madarchode", "maderchod",
            "chutiya", "chutiye", "chutiyapa", "chootiya",
            "bhosdike", "bhosadike", "bhosadi", "bhosda",
            "gaand", "gand", "gandu", "gaandu",
            "lauda", "loda", "louda", "lund", "lodu", "lawda",
            "randi", "raandi", "randwa",
            "harami", "haraami", "kameena", "kameene",
            "saale", "sale", "saala", "kutta", "kutte",
            "chut", "choot", "jhant", "jhaant",
            "mc", "bc"
    ));

    private static final Pattern REPEATED_CHARS_PATTERN = Pattern.compile("(.)\\1{2,}");

    /**
     * Checks if the given text contains any profanity or abusive language.
     */
    public boolean containsProfanity(String text) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }

        String normalized = normalizeText(text);

        // Split into tokens by non-alphanumeric characters
        String[] tokens = normalized.split("[^a-z0-9]+");

        for (String token : tokens) {
            if (token.isEmpty()) continue;

            // Direct check
            if (PROFANITY_WORDS.contains(token)) {
                return true;
            }

            // Check without trailing 's', 'ed', 'ing'
            if (token.endsWith("ing") && token.length() > 5) {
                String stem = token.substring(0, token.length() - 3);
                if (PROFANITY_WORDS.contains(stem)) return true;
            }
            if (token.endsWith("ed") && token.length() > 4) {
                String stem = token.substring(0, token.length() - 2);
                if (PROFANITY_WORDS.contains(stem)) return true;
            }
            if (token.endsWith("s") && token.length() > 3) {
                String stem = token.substring(0, token.length() - 1);
                if (PROFANITY_WORDS.contains(stem)) return true;
            }

            // Also check collapsed repetitions (e.g. fuuuuck -> fuck)
            String collapsed = collapseRepeatedChars(token);
            if (PROFANITY_WORDS.contains(collapsed)) {
                return true;
            }
        }

        // Substring / regex check for compound profanities
        for (String badWord : PROFANITY_WORDS) {
            // For 2-letter words (mc, bc), only strict word-boundary match
            if (badWord.length() <= 2) {
                Pattern p = Pattern.compile("\\b" + Pattern.quote(badWord) + "\\b");
                if (p.matcher(normalized).find()) {
                    return true;
                }
            } else {
                Pattern p = Pattern.compile("\\b" + Pattern.quote(badWord) + "\\b");
                if (p.matcher(normalized).find()) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Normalizes common leetspeak substitutions and lowercases.
     */
    private String normalizeText(String input) {
        String lower = input.toLowerCase(Locale.ENGLISH);

        // Leetspeak replacements
        lower = lower.replace('@', 'a')
                     .replace('$', 's')
                     .replace('!', 'i')
                     .replace('1', 'i')
                     .replace('0', 'o')
                     .replace('3', 'e')
                     .replace('+', 't')
                     .replace('*', ' ');

        return lower;
    }

    private String collapseRepeatedChars(String word) {
        return REPEATED_CHARS_PATTERN.matcher(word).replaceAll("$1");
    }
}
