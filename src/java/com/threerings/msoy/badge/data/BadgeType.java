//
// $Id$

package com.threerings.msoy.badge.data;

import java.util.Map;
import java.util.zip.CRC32;

import com.samskivert.util.HashIntMap;

import com.threerings.stats.Log;
import com.threerings.stats.data.Stat;
import com.threerings.stats.data.StatSet;

import com.threerings.msoy.badge.gwt.StampCategory;

import com.threerings.msoy.data.StatType;

/** Defines the various badge types. */
public enum BadgeType
{
    // social badges
    FRIEND(StampCategory.SOCIAL, StatType.FRIENDS_MADE, new Level[] {
        new Level(1, 1000),
        new Level(5, 2000),
        new Level(10, 3000),
        new Level(25, 5000),
        new Level(100, 10000),
        new Level(200, 20000),
        }) {
        protected int getAcquiredUnits (StatSet stats) {
            return stats.getIntStat(StatType.FRIENDS_MADE);
        }
    },

    /*FRIEND_1(StampCategory.SOCIAL, 1000, "Friends", 1) {
        protected int getAcquiredUnits (MemberObject user) {
            return user.stats.getIntStat(StatType.FRIENDS_MADE);
        }
    },

    FRIEND_2(StampCategory.SOCIAL, 2000, "Friends", 5) {
        protected int getAcquiredUnits (MemberObject user) {
            return user.stats.getIntStat(StatType.FRIENDS_MADE);
        }
    },

    WHIRLEDS_1(StampCategory.SOCIAL, 1000, "Whirleds", 1) {
        protected int getAcquiredUnits (MemberObject user) {
            return user.stats.getIntStat(StatType.WHIRLEDS_CREATED);
        }
    },

    INVITES_1(StampCategory.SOCIAL, 1000, "Invites", 1) {
        protected int getAcquiredUnits (MemberObject user) {
            return user.stats.getIntStat(StatType.INVITES_ACCEPTED);
        }
    },

    PLAYTIME_1(StampCategory.SOCIAL, 1000, "ActiveHours", 24) {
        protected int getAcquiredUnits (MemberObject user) {
            return (int)Math.floor(user.stats.getIntStat(StatType.MINUTES_ACTIVE) / 60);
        }
    },

    // game badges
    GAMER_1(StampCategory.GAME, 1000, "Games", 5) {
        protected int getAcquiredUnits (MemberObject user) {
            return user.stats.getSetStatSize(StatType.UNIQUE_GAMES_PLAYED);
        }
    },

    MULTIPLAYER_1(StampCategory.GAME, 2000, "MultiplayerWins", 1) {
        protected int getAcquiredUnits (MemberObject user) {
            return user.stats.getIntStat(StatType.MP_GAMES_WON);
        }
    },

    TROPHY_1(StampCategory.GAME, 1000, "Trophies", 1) {
        protected int getAcquiredUnits (MemberObject user) {
            return user.stats.getIntStat(StatType.TROPHIES_EARNED);
        }
    },*/

    ;

    public static class Level
    {
        public int requiredUnits;
        public int coinValue;

        Level (int requiredUnits, int coinValue)
        {
            this.requiredUnits = requiredUnits;
            this.coinValue = coinValue;
        }
    }

    /**
     * A main method so that this class can be run on its own for Badge code discovery.
     */
    public static void main (String[] args)
    {
        // dump all of the known badge types and their code
        System.out.println("  Code   -   Badge\n--------------------");
        for (Map.Entry<Integer, BadgeType> entry : _codeToType.entrySet()) {
            System.out.println(Integer.toHexString(entry.getKey()) + " - " + entry.getValue());
        }
    }

    /**
     * Maps a {@link BadgeType}'s code back to a {@link BadgeType} instance.
     */
    public static BadgeType getType (int code)
    {
        return _codeToType.get(code);
    }

    /**
     * Badge types can override this to apply constraints to Badges (e.g., only unlocked when
     * another badge is earned.)
     */
    public boolean isUnlocked (EarnedBadgeSet badges)
    {
        return true;
    }

    /**
     * @return the number of levels this badge has.
     */
    public int getNumLevels ()
    {
        return _levels.length;
    }

    /**
     * @return the level data for the specified badge level, or null if the level is out of range.
     */
    public Level getLevel (int level)
    {
        return (level >= 0 && level < _levels.length ? _levels[level] : null);
    }

    /**
     * Returns the progress that the specified user has made on this badge.
     */
    public BadgeProgress getProgress (StatSet stats)
    {
        int highestLevel = -1;
        int requiredUnits = 0;
        int acquiredUnits = getAcquiredUnits(stats);
        if (_levels != null) {
            for (Level level : _levels) {
                if (acquiredUnits >= level.requiredUnits) {
                    highestLevel++;
                } else {
                    requiredUnits = level.requiredUnits;
                    break;
                }
            }
        }

        return new BadgeProgress(highestLevel, requiredUnits, acquiredUnits);
    }

    /**
     * @return the unique code for this badge type, which is a function of its name.
     */
    public final int getCode()
    {
        return _code;
    }

    /**
     * @return the relevant StatType associated with this badge, or null if the badge doesn't have
     * one. The badge system uses this information to update badges when their associated stats
     * are updated.
     */
    public StatType getRelevantStat ()
    {
        return _relevantStat;
    }

    /**
     * @return the Category this badge falls under.
     */
    public StampCategory getCategory ()
    {
        return _category;
    }

    /**
     * Overridden by badge types to indicate how many units of the stat that this badge tracks
     * (games played, friends made, etc) the user has acquired.
     */
    protected int getAcquiredUnits (StatSet stats)
    {
        return 0;
    }

    /** Constructs a new BadgeType. */
    BadgeType (StampCategory category, StatType relevantStat, Level[] levels)
    {
        _category = category;
        _relevantStat = relevantStat;
        _levels = levels;

        // ensure the badge has at least one level
        if (_levels == null || _levels.length == 0) {
            _levels = new Level[] { new Level(0, 0) };
        }
    }

    /** Constructs a new BadgeType with no relevant stat and a single level. */
    BadgeType (StampCategory category, int coinValue)
    {
        this(category, null, new Level[] { new Level(0, coinValue) });
    }

    /**
     * Create the hash<->BadgeType mapping for each BadgeType.
     * This is done in a static block because it's an error for an enum
     * to access its static members in its constructor.
     */
    static
    {
        _crc = new CRC32();

        for (BadgeType type : BadgeType.values()) {
            type._code = mapCodeForType(type);
        }
    }

    protected static int mapCodeForType (BadgeType type)
    {
        // compute the CRC32 hash
        _crc.reset();
        _crc.update(type.name().getBytes());
        int code = (int) _crc.getValue();

        // store the hash in a map
        if (_codeToType == null) {
            _codeToType = new HashIntMap<BadgeType>();
        }
        if (_codeToType.containsKey(code)) {
            Log.log.warning("Badge type collision! " + type + " and " + _codeToType.get(code) +
                " both map to '" + code + "'.");
        } else {
            _codeToType.put(code, type);
        }

        return code;
    }

    protected int _code;
    protected StampCategory _category;
    protected StatType _relevantStat;
    protected Level[] _levels;

    /** The table mapping stat codes to enumerated types. */
    protected static HashIntMap<BadgeType> _codeToType;

    protected static CRC32 _crc;
};
