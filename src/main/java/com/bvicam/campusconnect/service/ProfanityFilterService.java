package com.bvicam.campusconnect.service;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Content moderation service that detects profanity and abusive language in user-submitted text.
 */
@Service
public class ProfanityFilterService {

    // Focused English and Hindi/Hinglish blocklist (avoids ambiguous everyday words like "sale", "mc", "bc")
    private static final Set<String> PROFANITY_WORDS = Set.of(
            "fuck", "fucker", "fucking", "fucked", "fuckup",
            "shit", "shitty", "bullshit", "dipshit",
            "asshole", "dumbass", "jackass", "badass",
            "bitch", "bitching", "bitches",
            "bastard", "cunt", "dick", "dickhead", "cock", "cocksucker",
            "pussy", "fag", "faggot", "slut", "whore",
            "motherfucker", "motherfucking", "retard", "retarded",
            "douche", "douchebag", "prick", "twat", "wanker", "bollocks",
            "blowjob", "nigger", "nigga",

            "bhenchod", "behenchod", "behenchode", "bhanchod", "bhenchode",
            "madarchod", "madarchode", "maderchod",
            "chutiya", "chutiye", "chutiyapa", "chootiya",
            "bhosdike", "bhosadike", "bhosadi", "bhosda",
            "gaand", "gand", "gandu", "gaandu",
            "lauda", "loda", "louda", "lund", "lodu", "lawda",
            "randi", "raandi", "randwa",
            "harami", "haraami", "kameena", "kameene",
            "saale", "saala", "kutta", "kutte",
            "chut", "choot", "jhant", "jhaant"
    );

    private static final Pattern REPEATED_CHARS_PATTERN = Pattern.compile("(.)\\1{2,}");
    private static final List<Pattern> WORD_BOUNDARY_PATTERNS = PROFANITY_WORDS.stream()
            .map(w -> Pattern.compile("\\b" + Pattern.quote(w) + "\\b"))
            .toList();

    public boolean containsProfanity(String text) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }

        String normalized = normalizeText(text);
        String[] tokens = normalized.split("[^a-z0-9]+");

        for (String token : tokens) {
            if (token.isEmpty()) continue;

            if (PROFANITY_WORDS.contains(token)) {
                return true;
            }
            if (token.endsWith("ing") && token.length() > 5 && PROFANITY_WORDS.contains(token.substring(0, token.length() - 3))) {
                return true;
            }
            if (token.endsWith("ed") && token.length() > 4 && PROFANITY_WORDS.contains(token.substring(0, token.length() - 2))) {
                return true;
            }
            if (token.endsWith("s") && token.length() > 3 && PROFANITY_WORDS.contains(token.substring(0, token.length() - 1))) {
                return true;
            }

            String collapsed = collapseRepeatedChars(token);
            if (PROFANITY_WORDS.contains(collapsed)) {
                return true;
            }
        }

        for (Pattern pattern : WORD_BOUNDARY_PATTERNS) {
            if (pattern.matcher(normalized).find()) {
                return true;
            }
        }

        return false;
    }

    private String normalizeText(String input) {
        String lower = input.toLowerCase(Locale.ENGLISH);
        return lower.replace('@', 'a')
                .replace('$', 's')
                .replace('!', 'i')
                .replace('1', 'i')
                .replace('0', 'o')
                .replace('3', 'e')
                .replace('+', 't')
                .replace('*', ' ');
    }

    private String collapseRepeatedChars(String word) {
        return REPEATED_CHARS_PATTERN.matcher(word).replaceAll("$1");
    }
}
