package com.alrex.parcool.client.input;

import com.alrex.parcool.ParCool;
import com.alrex.parcool.compat.IKeyMappingDuck;
import dev.architectury.registry.client.keymappings.KeyMappingRegistry;
import com.alrex.parcool.utilities.VectorUtil;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

public class KeyBindings {

    /**
     * 1.21.11 replaced the plain category string with a {@link KeyMapping.Category} record, whose
     * label is the translation key {@code id.toLanguageKey("key.category")} - so the same ParCool
     * entry moved from {@code key.categories.parcool} to {@code key.category.parcool}.
     */
    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(ParCool.MOD_ID, "parcool"));


    /**
     * Resolved lazily, never in a static field: Fabric invokes the client entrypoint from inside
     * {@code Minecraft}'s constructor, so a class initialiser that runs at mod-init time would cache
     * a {@code null} Minecraft/Options and NPE on the first input poll.
     */
    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    private static Options options() {
        return Minecraft.getInstance().options;
    }
    private static final KeyMapping keyBindEnable = new KeyMapping("key.parcool.Enable", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_P, CATEGORY);
	private static final KeyMapping keyBindCrawl = new KeyMapping("key.parcool.Crawl", GLFW.GLFW_KEY_C, CATEGORY);
	private static final KeyMapping keyBindGrabWall = new KeyMapping("key.parcool.ClingToCliff", InputConstants.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_RIGHT, CATEGORY);
	private static final KeyMapping keyBindBreakfall = new KeyMapping("key.parcool.Breakfall", GLFW.GLFW_KEY_R, CATEGORY);
	private static final KeyMapping keyBindFastRunning = new KeyMapping("key.parcool.FastRun", GLFW.GLFW_KEY_LEFT_CONTROL, CATEGORY);
	private static final KeyMapping keyBindFlipping = new KeyMapping("key.parcool.Flipping", GLFW.GLFW_KEY_UNKNOWN, CATEGORY);
	private static final KeyMapping keyBindVault = new KeyMapping("key.parcool.Vault", InputConstants.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_RIGHT, CATEGORY);
	private static final KeyMapping keyBindDodge = new KeyMapping("key.parcool.Dodge", GLFW.GLFW_KEY_R, CATEGORY);
    private static final KeyMapping keyBindRideZipline = new KeyMapping("key.parcool.RideZipline", InputConstants.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_RIGHT, CATEGORY);
	private static final KeyMapping keyBindWallJump = new KeyMapping("key.parcool.WallJump", GLFW.GLFW_KEY_SPACE, CATEGORY);
	private static final KeyMapping keyBindHangDown = new KeyMapping("key.parcool.HangDown", InputConstants.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_RIGHT, CATEGORY);
	private static final KeyMapping keyBindWallSlide = new KeyMapping("key.parcool.WallSlide", InputConstants.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_RIGHT, CATEGORY);
    private static final KeyMapping keyBindHideInBlock = new KeyMapping("key.parcool.HideInBlock", GLFW.GLFW_KEY_C, CATEGORY);
	private static final KeyMapping keyBindHorizontalWallRun = new KeyMapping("key.parcool.HorizontalWallRun", GLFW.GLFW_KEY_R, CATEGORY);
	private static final KeyMapping keyBindQuickTurn = new KeyMapping("key.parcool.QuickTurn", GLFW.GLFW_KEY_UNKNOWN, CATEGORY);
	private static final KeyMapping keyBindOpenSettings = new KeyMapping("key.parcool.openSetting", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_P, CATEGORY);
	private static final Vec3 forwardVector = new Vec3(0, 0, 1);

    /** All ParCool bindings, in registration order. */
    private static final KeyMapping[] ALL = {
            keyBindEnable, keyBindCrawl, keyBindGrabWall, keyBindBreakfall, keyBindFastRunning,
            keyBindDodge, keyBindRideZipline, keyBindWallSlide, keyBindWallJump, keyBindVault,
            keyBindHorizontalWallRun, keyBindHideInBlock, keyBindOpenSettings, keyBindQuickTurn,
            keyBindFlipping, keyBindHangDown
    };

    /** Every ParCool binding, for diagnostics and the register loop. */
    public static KeyMapping[] all() {
        return ALL;
    }

    /** Was NeoForge's patched {@code KeyMapping#getKey()}. */
    public static InputConstants.Key keyOf(KeyMapping mapping) {
        return ((IKeyMappingDuck) mapping).parcool$getKey();
    }

    /** True when both bindings resolve to the same physical key (the sneak-key conflict checks). */
    public static boolean isSameKey(KeyMapping a, KeyMapping b) {
        return keyOf(a).equals(keyOf(b));
    }

    /**
     * Ctrl+P / Alt+P equivalent on loaders without NeoForge's {@code KeyModifier}.
     * <p>
     * NeoForge's six-argument {@code KeyMapping} constructor attached a {@code KeyModifier} to the
     * binding itself, so vanilla's "is this key down" check only reported true while the modifier was
     * held. Vanilla has no such notion, so the modifier is sampled from the live window state and
     * combined with the raw physical key state. This keeps the upstream UX identical on both loaders.
     */
    private static boolean isMetaKeyDown(KeyMapping mapping, int modifier) {
        InputConstants.Key key = keyOf(mapping);
        if (key.getType() == InputConstants.Type.MOUSE) return false;
        if (!isPollableKeysym(key)) return false;
        Window window = mc().getWindow();
        boolean modifierDown = switch (modifier) {
            case GLFW.GLFW_MOD_CONTROL -> InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_CONTROL);
            case GLFW.GLFW_MOD_ALT -> InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_ALT);
            default -> true;
        };
        if (!modifierDown) return false;
        return InputConstants.isKeyDown(window, key.getValue());
    }

    /** Ctrl + the enable binding. */
    public static boolean isKeyEnableParCoolDown() {
        return isMetaKeyDown(keyBindEnable, GLFW.GLFW_MOD_CONTROL);
    }

    /**
     * Physical key state of a ParCool binding.
     *
     * <h2>Why this exists</h2>
     * Minecraft 1.21.1 stores bindings in {@code KeyMapping.MAP}, a {@code Map<Key, KeyMapping>} with
     * <b>one entry per physical key</b>: {@code KeyMapping.set(key, state)} and
     * {@code KeyMapping.click(key)} look the key up in that map and only ever touch the single mapping
     * stored there. ParCool binds 16 keys that vanilla already owns — right mouse (five bindings),
     * {@code R} (three), {@code C} (two), {@code Space}, {@code P} (two) — so registering them makes
     * the <i>last</i> ParCool binding win the map entry and the vanilla mapping for that key stops
     * receiving press/release events. In practice that meant right-click no longer placed blocks,
     * space no longer jumped, and so on.
     *
     * <p>Reading the raw window state sidesteps the map entirely, so several bindings can share one
     * physical key and vanilla keeps working. The {@link net.minecraft.client.KeyMapping} objects
     * are still created and registered so the keys appear in the Controls screen and can be
     * rebound; they are only used for their name, category and bound key, never for their state.
     */
    /**
     * Whether {@code glfwGetKey} can be handed this keysym.
     *
     * <h2>Why this exists</h2>
     * An unbound ParCool action - Flipping and QuickTurn default to {@link GLFW#GLFW_KEY_UNKNOWN} -
     * carries the keysym {@code -1}. {@code glfwGetKey} rejects it: GLFW raises
     * {@code GLFW_INVALID_ENUM} ({@code 0x00010003}, which is the {@code 65539} in the log) with the
     * message {@code "Invalid key -1"}, and {@code Window#defaultErrorCallback} turns that into the
     * three-line "########## GL ERROR ##########" block. Both of the unbound bindings are polled on
     * every {@link KeyRecorder#onClientTick()}, so the game printed roughly forty of those blocks a
     * second for as long as a world was loaded.
     *
     * <p>GLFW's own bounds check rejects anything below {@link GLFW#GLFW_KEY_SPACE} (32) as well, so
     * the test is the range GLFW accepts rather than a special case for {@code -1}.
     */
    private static boolean isPollableKeysym(InputConstants.Key key) {
        return key.getValue() >= GLFW.GLFW_KEY_SPACE;
    }

    public static boolean isDown(KeyMapping mapping) {
        InputConstants.Key key = keyOf(mapping);
        Window window = mc().getWindow();
        return switch (key.getType()) {
            case KEYSYM -> isPollableKeysym(key) && InputConstants.isKeyDown(window, key.getValue());
            case MOUSE -> GLFW.glfwGetMouseButton(window.handle(), key.getValue()) == GLFW.GLFW_PRESS;
            // A raw scancode binding cannot be polled through the keycode API; fall back to the
            // mapping's own state, which is correct as long as the key is not shared.
            case SCANCODE -> mapping.isDown();
        };
    }

    /** Alt + the settings binding. */
    public static boolean isKeyOpenSettingsDown() {
        return isMetaKeyDown(keyBindOpenSettings, GLFW.GLFW_MOD_ALT);
    }

	public static KeyMapping getKeySprint() {
		return options().keySprint;
	}

	public static Boolean isKeyJumpDown() {
		return mc().player != null
				&& mc().player.input != null
				&& mc().player.input.keyPresses.jump();
	}

	public static KeyMapping getKeySneak() {
		return options().keyShift;
	}

	public static Vec3 getCurrentMoveVector() {
		var player = Minecraft.getInstance().player;
		if (player == null) return Vec3.ZERO;
		var vector = player.input.getMoveVector();
		if (VectorUtil.isZero(vector)) return Vec3.ZERO;
		double length = vector.length();
		return new Vec3(vector.x / length, 0, vector.y / length);
	}

	public static Vec3 getForwardVector() {
		return forwardVector;
	}

	public static Boolean isAnyMovingKeyDown() {
		return mc().player != null
				&& mc().player.input != null
				&& (mc().player.input.keyPresses.left()
				|| mc().player.input.keyPresses.right()
				|| mc().player.input.keyPresses.forward()
				|| mc().player.input.keyPresses.backward());
	}

	public static Boolean isLeftAndRightDown() {
		return mc().player != null && mc().player.input != null
				&& mc().player.input.keyPresses.left() && mc().player.input.keyPresses.right();
	}

	public static Boolean isKeyForwardDown() {
		return mc().player != null && mc().player.input != null && mc().player.input.keyPresses.forward();
	}

	public static Boolean isKeyLeftDown() {
		return mc().player != null && mc().player.input != null && mc().player.input.keyPresses.left();
	}

	public static Boolean isKeyRightDown() {
		return mc().player != null && mc().player.input != null && mc().player.input.keyPresses.right();
	}

	public static Boolean isKeyBackDown() {
		return mc().player != null && mc().player.input != null && mc().player.input.keyPresses.backward();
	}

    public static KeyMapping getKeyBindEnable() {
        return keyBindEnable;
    }

	public static KeyMapping getKeyCrawl() {
		return keyBindCrawl;
	}

	public static KeyMapping getKeyQuickTurn() {
		return keyBindQuickTurn;
	}

	public static KeyMapping getKeyGrabWall() {
		return keyBindGrabWall;
	}

	public static KeyMapping getKeyVault() {
		return keyBindVault;
	}

	public static KeyMapping getKeyActivateParCool() {
		return keyBindOpenSettings;
	}

	public static KeyMapping getKeyBreakfall() {
		return keyBindBreakfall;
	}

	public static KeyMapping getKeyFastRunning() {
		return keyBindFastRunning;
	}

	public static KeyMapping getKeyDodge() {
		return keyBindDodge;
	}

    public static KeyMapping getKeyRideZipline() {
        return keyBindRideZipline;
    }

	public static KeyMapping getKeyWallSlide() {
		return keyBindWallSlide;
	}

	public static KeyMapping getKeyHangDown() {
		return keyBindHangDown;
	}

    public static KeyMapping getKeyHideInBlock() {
        return keyBindHideInBlock;
    }

	public static KeyMapping getKeyHorizontalWallRun() {
		return keyBindHorizontalWallRun;
	}

	public static KeyMapping getKeyWallJump() {
		return keyBindWallJump;
	}

	public static KeyMapping getKeyFlipping() {
		return keyBindFlipping;
	}

	/**
	 * Was {@code RegisterKeyMappingsEvent}. {@link KeyMappingRegistry} is the Architectury wrapper.
	 * <p>
	 * The two "meta" bindings (enable / open settings) used NeoForge's six-argument
	 * {@code KeyMapping} constructor, which carries a {@code KeyConflictContext} and a
	 * {@code KeyModifier} (Ctrl / Alt). Neither exists on Fabric, so those two are registered with the
	 * vanilla constructor and the modifier is checked at press time instead — see
	 * {@link #isKeyActivateParCool()} and {@link #isKeyOpenSettingsPressed()}.
	 */
	public static void register() {
		for (KeyMapping mapping : ALL) {
			KeyMappingRegistry.register(mapping);
		}
	}

    private static boolean isParCool(KeyMapping mapping) {
        return mapping.getName().startsWith("key.parcool.");
    }
}
