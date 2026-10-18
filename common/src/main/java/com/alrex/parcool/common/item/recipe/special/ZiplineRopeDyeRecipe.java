package com.alrex.parcool.common.item.recipe.special;

import com.alrex.parcool.common.item.Items;
import com.alrex.parcool.common.item.recipe.Recipes;
import com.alrex.parcool.common.item.zipline.ZiplineRopeItem;
import javax.annotation.Nonnull;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.level.Level;
import net.minecraft.util.Mth;
import net.minecraft.util.FastColor;

public class ZiplineRopeDyeRecipe extends CustomRecipe {

    // 1.20.4 recipes carry only the category: CustomRecipe(CraftingBookCategory), which is what
    // SimpleCraftingRecipeSerializer's Factory constructs. (The recipe id field is a 1.20.1-only
    // shape; it comes back as part of the RecipeHolder rework in 1.20.5.)
    public ZiplineRopeDyeRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(@Nonnull CraftingContainer input, @Nonnull Level level) {
        boolean ziplineRopeFound = false;
        boolean dyeItemFound = false;
        for (int i = 0; i < input.getContainerSize(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.getItem().equals(Items.ZIPLINE_ROPE.get())) {
                if (ziplineRopeFound) return false;
                else ziplineRopeFound = true;
            } else if (stack.getItem() instanceof DyeItem) {
                dyeItemFound = true;
            } else if (!stack.isEmpty()) {
                return false;
            }
        }
        return ziplineRopeFound && dyeItemFound;
    }

    @Nonnull
    @Override
    public ItemStack assemble(CraftingContainer input, @Nonnull RegistryAccess registryAccess) {
        ItemStack ziplineRope = null;
        java.util.LinkedList<DyeItem> dyeItems = new java.util.LinkedList<>();
        for (int i = 0; i < input.getContainerSize(); i++) {
            ItemStack stack = input.getItem(i);
            Item item = stack.getItem();
            if (item instanceof ZiplineRopeItem) {
                ziplineRope = stack;
            } else if (item instanceof DyeItem) {
                dyeItems.add((DyeItem) item);
            } else if (!stack.isEmpty()) {
                return ItemStack.EMPTY;
            }
        }
        if (ziplineRope == null || dyeItems.isEmpty()) return ItemStack.EMPTY;
        ItemStack resultZiplineRope = new ItemStack(Items.ZIPLINE_ROPE.get());
        // Carry the bound hook and the previous colour over before averaging the new dye in: without
        // this the recipe would silently clear both.
        resultZiplineRope.setTag(ziplineRope.getTag());
        int r = 0, g = 0, b = 0;
        int dyeSize = dyeItems.size();
        for (DyeItem dyeItem : dyeItems) {
            float[] color = dyeItem.getDyeColor().getTextureDiffuseColors();
            r += (int) (color[0] * 255f);
            g += (int) (color[1] * 255f);
            b += (int) (color[2] * 255f);
        }
        if (ZiplineRopeItem.hasCustomColor(resultZiplineRope)) {
            dyeSize++;
            int color = ZiplineRopeItem.getColor(resultZiplineRope);
            r += FastColor.ARGB32.red(color);
            g += FastColor.ARGB32.green(color);
            b += FastColor.ARGB32.blue(color);
        }
        r = Mth.clamp(r / dyeSize, 0, 0xFF);
        g = Mth.clamp(g / dyeSize, 0, 0xFF);
        b = Mth.clamp(b / dyeSize, 0, 0xFF);
        // FastColor.ARGB32 only has the four-argument color(a, r, g, b) before 1.20.5; the
        // three-argument overload that defaults the alpha to opaque comes with 1.20.5.
        ZiplineRopeItem.setColor(resultZiplineRope, FastColor.ARGB32.color(0xFF, r, g, b));
        return resultZiplineRope;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Nonnull
    @Override
    public RecipeSerializer<?> getSerializer() {
        return Recipes.ZIPLINE_ROPE_DYE.get();
    }
}
