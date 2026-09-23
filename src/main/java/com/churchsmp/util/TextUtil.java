package com.churchsmp.util;

import com.churchsmp.weapon.LegendaryWeapon;

public class TextUtil {

    /**
     * Converts alphabetical characters in text to Unicode small capitals.
     * Preserves MiniMessage tags like <gradient:...>, <color:...>, <bold>, etc.
     */
    public static String toSmallCaps(String input) {
        if (input == null) return "";
        StringBuilder sb = new StringBuilder();
        boolean inTag = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '<') {
                inTag = true;
                sb.append(c);
            } else if (c == '>') {
                inTag = false;
                sb.append(c);
            } else if (inTag) {
                sb.append(c);
            } else {
                sb.append(toSmallCapChar(c));
            }
        }
        return sb.toString();
    }

    public static char toSmallCapChar(char c) {
        return switch (Character.toUpperCase(c)) {
            case 'A' -> 'ᴀ';
            case 'B' -> 'ʙ';
            case 'C' -> 'ᴄ';
            case 'D' -> 'ᴅ';
            case 'E' -> 'ᴇ';
            case 'F' -> 'ғ';
            case 'G' -> 'ɢ';
            case 'H' -> 'ʜ';
            case 'I' -> 'ɪ';
            case 'J' -> 'ᴊ';
            case 'K' -> 'ᴋ';
            case 'L' -> 'ʟ';
            case 'M' -> 'ᴍ';
            case 'N' -> 'ɴ';
            case 'O' -> 'ᴏ';
            case 'P' -> 'ᴘ';
            case 'Q' -> 'ǫ';
            case 'R' -> 'ʀ';
            case 'S' -> 'ꜱ';
            case 'T' -> 'ᴛ';
            case 'U' -> 'ᴜ';
            case 'V' -> 'ᴠ';
            case 'W' -> 'ᴡ';
            case 'X' -> 'x';
            case 'Y' -> 'ʏ';
            case 'Z' -> 'ᴢ';
            default -> c;
        };
    }

    /**
     * Formats a command header with white-gold small caps font.
     */
    public static String formatCommandHeader(String title) {
        return "<gold>═════[ <white><bold>" + toSmallCaps(title) + "</bold></white> ]═════</gold>";
    }

    /**
     * Builds the chat message sent to the player when a legendary ability is used:
     * "Used [....] To [....] , [.....] , [.....] it is now on cooldown for [....]."
     * in the weapon's color theme gradient and small caps.
     */
    public static String getAbilityUsedMessage(LegendaryWeapon weapon, String abilityName, int cooldownSeconds) {
        String grad = weapon.getThemeGradientTag();
        String weaponCaps = toSmallCaps(weapon.getId().replace('_', ' '));
        String abilityCaps = toSmallCaps(abilityName);
        return grad + "<bold>✦ ᴜꜱᴇᴅ [" + weaponCaps + "] ᴛᴏ [" + abilityCaps + "], ɪᴛ ɪꜱ ɴᴏᴡ ᴏɴ ᴄᴏᴏʟᴅᴏᴡɴ ғᴏʀ " + cooldownSeconds + "ꜱ.</bold></gradient>";
    }
}
