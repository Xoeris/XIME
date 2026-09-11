package xime.ai;

import org.junit.Test;
import static org.junit.Assert.*;

public class LocalIntentParserTest {
    private final LocalIntentParser p = new LocalIntentParser();

    @Test public void mediaPause() { assertEquals(Intent.MEDIA_PAUSE, p.parse("pause").intent); assertEquals(Intent.MEDIA_PAUSE, p.parse("Hey Sarah pause").intent); assertEquals(Intent.MEDIA_PAUSE, p.parse("stop").intent); }
    @Test public void mediaPlay() { assertEquals(Intent.MEDIA_PLAY, p.parse("play").intent); assertEquals(Intent.MEDIA_PLAY, p.parse("resume").intent); }
    @Test public void mediaNext() { assertEquals(Intent.MEDIA_NEXT, p.parse("next").intent); assertEquals(Intent.MEDIA_NEXT, p.parse("skip").intent); }
    @Test public void mediaVolume() {
        ParsedCommand up = p.parse("volume up"); assertEquals(Intent.MEDIA_VOLUME, up.intent); assertEquals("+1", up.params.get("delta"));
        ParsedCommand down = p.parse("louder"); assertEquals("+1", down.params.get("delta"));
        ParsedCommand set = p.parse("set volume to 7"); assertEquals("7", set.params.get("value"));
    }
    @Test public void systemLock() { assertEquals(Intent.SYSTEM_LOCK, p.parse("lock my laptop").intent); }
    @Test public void systemSleep() { assertEquals(Intent.SYSTEM_SLEEP, p.parse("go to sleep").intent); }
    @Test public void systemShutdown() { assertEquals(Intent.SYSTEM_SHUTDOWN, p.parse("shutdown").intent); assertEquals(Intent.SYSTEM_SHUTDOWN, p.parse("shut down").intent); }
    @Test public void systemStatus() { assertEquals(Intent.SYSTEM_STATUS, p.parse("status").intent); }
    @Test public void appLaunch() { ParsedCommand c = p.parse("open Spotify"); assertEquals(Intent.APP_LAUNCH, c.intent); assertEquals("spotify", c.params.get("target").toString().toLowerCase()); }
    @Test public void tvInput() { ParsedCommand c = p.parse("switch to HDMI 2"); assertEquals(Intent.TV_INPUT_SWITCH, c.intent); assertTrue(c.params.get("source").toString().contains("HDMI")); }
    @Test public void tvPower() { assertEquals(Intent.TV_POWER, p.parse("turn off the TV").intent); }
    @Test public void navigate() { ParsedCommand c = p.parse("open browser on laptop"); assertEquals(Intent.NAVIGATE, c.intent); assertEquals("LAPTOP", c.params.get("deviceHint")); }
    @Test public void queryFallback() {
        ParsedCommand c = p.parse("blablah flurb unknown phrase 123");
        assertEquals(Intent.QUERY, c.intent);
        assertTrue(c.params.containsKey("raw"));
        assertEquals("blablah flurb unknown phrase 123", c.rawText);
    }
    @Test public void rawPreserved() { ParsedCommand c = p.parse("Pause"); assertEquals("Pause", c.rawText); }
    @Test public void heySarahStripped() { assertEquals(Intent.MEDIA_PAUSE, p.parse("Hey Sarah, pause").intent); }
}
