/*
 * Copyright 2010, 2011 Institut Pasteur.
 * 
 * This file is part of ICY.
 * 
 * ICY is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * ICY is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with ICY. If not, see <http://www.gnu.org/licenses/>.
 */
package icy.util;

import icy.math.MathUtil;

/**
 * @author stephane
 */
public class StringUtil
{
    /**
     * Return the index of previous digit char from specified index in specified string<br>
     * return -1 if not found
     */
    public static int getPreviousDigitCharIndex(String value, int from)
    {
        final int len = value.length();

        if (from >= len)
            return -1;

        int index = from;
        while (index >= 0)
        {
            if (Character.isDigit(value.charAt(index)))
                return index;
            index--;
        }

        return -1;

    }

    /**
     * Return the index of previous non digit char from specified index in specified string<br>
     * return -1 if not found
     */
    public static int getPreviousNonDigitCharIndex(String value, int from)
    {
        final int len = value.length();

        if (from >= len)
            return -1;

        int index = from;
        while (index >= 0)
        {
            if (!Character.isDigit(value.charAt(index)))
                return index;
            index--;
        }

        return -1;
    }

    /**
     * Return the index of next digit char from specified index in specified string<br>
     * return -1 if not found
     */
    public static int getNextDigitCharIndex(String value, int from)
    {
        final int len = value.length();

        if (from < 0)
            return -1;

        int index = from;
        while (index < len)
        {
            if (Character.isDigit(value.charAt(index)))
                return index;
            index++;
        }

        return -1;
    }

    /**
     * Return the index of next non digit char from specified index in specified string<br>
     * return -1 if not found
     */
    public static int getNextNonDigitCharIndex(String value, int from)
    {
        final int len = value.length();

        if (from < 0)
            return -1;

        int index = from;
        while (index < len)
        {
            if (!Character.isDigit(value.charAt(index)))
                return index;
            index++;
        }

        return -1;
    }

    /**
     * Limit the length of the specified string to maxlen
     */
    public static String limit(String value, int maxlen, boolean tailLimit)
    {
        if (value == null)
            return null;

        final int len = value.length();

        if (len > maxlen)
        {
            // simple truncation
            if (tailLimit || (maxlen <= 8))
                return value.substring(0, maxlen - 2) + "..";

            // cut center
            final int cut = (maxlen - 3) / 2;
            return value.substring(0, cut) + "..." + value.substring(len - cut);
        }

        return value;
    }

    /**
     * Limit the length of the specified string to maxlen
     */
    public static String limit(String value, int maxlen)
    {
        return limit(value, maxlen, false);
    }

    /**
     * Return true if the specified String are exactly the same
     */
    public static boolean equals(String s1, String s2)
    {
        if (isEmpty(s1))
            return isEmpty(s2);

        return s1.equals(s2);
    }

    /**
     * Return true if the specified String is empty.
     * 
     * @param trim
     *        trim the String before doing the empty test
     */
    public static boolean isEmpty(String value, boolean trim)
    {
        if (value != null)
        {
            if (trim)
                return value.trim().length() == 0;

            return value.length() == 0;
        }

        return true;
    }

    /**
     * Return true if the specified String is empty.
     * The String is trimed by default before doing the test
     */
    public static boolean isEmpty(String value)
    {
        return isEmpty(value, true);
    }

    /**
     * Try to parse a boolean from the specified String and return it.
     * Return 'def' is we can't parse any boolean from the string.
     */
    public static boolean parseBoolean(String s, boolean def)
    {
        if (s == null)
            return def;

        final String value = s.toLowerCase();

        if (value.equals(Boolean.toString(true)))
            return true;
        if (value.equals(Boolean.toString(false)))
            return false;

        return def;
    }

    /**
     * Try to parse a integer from the specified String and return it.
     * Return 'def' is we can't parse any integer from the string.
     */
    public static int parseInt(String s, int def)
    {
        try
        {
            return Integer.parseInt(s);
        }
        catch (NumberFormatException E)
        {
            return def;
        }
    }

    /**
     * Try to parse a long integer from the specified String and return it.
     * Return 'def' is we can't parse any integer from the string.
     */
    public static long parseLong(String s, long def)
    {
        try
        {
            return Long.parseLong(s);
        }
        catch (NumberFormatException E)
        {
            return def;
        }
    }

    /**
     * Try to parse a float from the specified String and return it.
     * Return 'def' is we can't parse any float from the string.
     */
    public static float parseFloat(String s, float def)
    {
        try
        {
            return Float.parseFloat(s);
        }
        catch (NumberFormatException E)
        {
            return def;
        }
    }

    /**
     * Try to parse a double from the specified String and return it.
     * Return 'def' is we can't parse any double from the string.
     */
    public static double parseDouble(String s, double def)
    {
        try
        {
            return Double.parseDouble(s);
        }
        catch (NumberFormatException E)
        {
            return def;
        }
    }

    /**
     * Try to parse a array of byte from the specified String and return it.
     * Return 'def' is we can't parse any array of byte from the string.
     */
    public static byte[] parseBytes(String s, byte[] def)
    {
        if (s == null)
            return def;

        return s.getBytes();
    }

    /**
     * Returns a <tt>String</tt> object representing the specified
     * boolean. If the specified boolean is <code>true</code>, then
     * the string {@code "true"} will be returned, otherwise the
     * string {@code "false"} will be returned.
     */
    public static String toString(boolean value)
    {
        return Boolean.toString(value);
    }

    /**
     * Returns a <code>String</code> object representing the specified integer.
     */
    public static String toString(int value)
    {
        return Integer.toString(value);
    }

    /**
     * Returns a <code>String</code> object representing the specified integer.<br>
     * If the returned String is shorter than specified length<br>
     * then leading '0' are added to the string.
     */
    public static String toString(int value, int minSize)
    {
        String result = Integer.toString(value);

        while (result.length() < minSize)
            result = "0" + result;

        return result;
    }

    /**
     * Returns a <code>String</code> object representing the specified <code>long</code>.
     */
    public static String toString(long value)
    {
        return Long.toString(value);
    }

    /**
     * Returns a string representation of the <code>float</code> argument.
     */
    public static String toString(float value)
    {
        return Float.toString(value);
    }

    /**
     * Returns a string representation of the <code>double</code> argument.
     */
    public static String toString(double value)
    {
        final int i = (int) value;

        if (i == value)
            return toString(i);

        return Double.toString(value);
    }

    /**
     * Returns a string representation of the <code>double</code> argument
     * with specified number of decimal.
     */
    public static String toString(double value, int numDecimal)
    {
        return Double.toString(MathUtil.round(value, numDecimal));
    }

    /**
     * Returns a string representation of the <code>double</code> argument with specified size :<br>
     * <code>toString(1.23456, 5)</code> --> <code>"1.2345"</code><br>
     * <code>toString(123.4567, 4)</code> --> <code>"123.4"</code><br>
     * <code>toString(1234.567, 2)</code> --> <code>"1234"</code> as we never trunk integer part.<br>
     * <code>toString(1234.5, 10)</code> --> <code>"1234.5"</code> as we never trunk integer part.<br>
     */
    public static String toStringEx(double value, int size)
    {
        final int i = (int) value;

        if (i == value)
            return toString(i);

        return Double.toString(MathUtil.roundSignificant(value, size, true));
    }

    /**
     * Return a string representation of the byte array argument.
     */
    public static String toString(byte[] value)
    {
        return new String(value);
    }

    /**
     * Returns a string representation of the integer argument as an
     * unsigned integer in base 16.
     */
    public static String toHexaString(int value)
    {
        return Integer.toHexString(value);
    }

    /**
     * Returns a string representation of the integer argument as an
     * unsigned integer in base 16.<br>
     * Force the returned string to have the specified size :<br>
     * If the string is longer then only last past is kept.<br>
     * If the string is shorter then leading 0 are added to the string.
     */
    public static String toHexaString(int value, int size)
    {
        String result = Integer.toHexString(value);

        if (result.length() > size)
            return result.substring(result.length() - size);

        while (result.length() < size)
            result = "0" + result;
        return result;
    }
}
