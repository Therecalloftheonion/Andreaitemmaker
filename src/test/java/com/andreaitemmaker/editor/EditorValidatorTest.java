package com.andreaitemmaker.editor;

import com.andreaitemmaker.api.CustomItemType;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The editor must attribute every problem to the exact field, and must never let a file through
 * that the content loader would reject at load time.
 */
class EditorValidatorTest {

    @TempDir
    Path tempDir;

    private static final Set<String> MECHANICS = Set.of("heal", "sound", "lightning", "armor-effects");

    private EditorDocument document(String name, String yaml) throws IOException {
        File file = new File(tempDir.toFile(), name);
        Files.writeString(file.toPath(), yaml, StandardCharsets.UTF_8);
        return EditorDocument.load(file);
    }

    private EditorValidator.Context emptyContext() {
        return new EditorValidator.Context(Set.of(), Map.of(), Set.of(), MECHANICS, tempDir.toFile());
    }

    private List<EditorIssue> validate(EditorDocument doc, CustomItemType type) {
        return EditorValidator.validate(doc, type, emptyContext());
    }

    private static void assertNoErrors(List<EditorIssue> issues) {
        assertFalse(EditorValidator.hasErrors(issues),
                () -> "expected no errors but found: " + issues.stream()
                        .filter(EditorIssue::isError).map(EditorIssue::describe).toList());
    }

    private static EditorIssue errorFor(List<EditorIssue> issues, String field) {
        return issues.stream()
                .filter(EditorIssue::isError)
                .filter(issue -> issue.field().equals(field))
                .findFirst()
                .orElse(null);
    }

    private static boolean hasWarningFor(List<EditorIssue> issues, String field) {
        return issues.stream().anyMatch(issue -> !issue.isError() && issue.field().equals(field));
    }

    @Test
    void validWeaponHasNoErrors() throws IOException {
        EditorDocument doc = document("storm_blade.yml", """
                type: WEAPON
                material: DIAMOND_SWORD
                display-name: "&bStorm Blade"
                lore:
                  - "&7A blade."
                attributes:
                  attack_damage: 9.0
                enchantments:
                  sharpness: 3
                unbreakable: true
                mechanics:
                  lightning:
                    damage: 4.0
                """);
        assertNoErrors(validate(doc, CustomItemType.WEAPON));
    }

    @Test
    void validBlockHasNoErrors() throws IOException {
        EditorDocument doc = document("crate.yml", """
                id: crate
                material: STICK
                base-block: WHITE_WOOL
                drops-item: true
                """);
        assertNoErrors(validate(doc, CustomItemType.BLOCK));
    }

    @Test
    void invalidIdIsAnError() throws IOException {
        EditorDocument doc = document("bad.yml", "id: Bad Id!\nmaterial: STICK\n");
        List<EditorIssue> issues = validate(doc, CustomItemType.ITEM);
        assertTrue(EditorValidator.hasErrors(issues));
        assertEquals("id", errorFor(issues, "id").field());
    }

    @Test
    void duplicateIdIsAnError() throws IOException {
        EditorDocument doc = document("taken.yml", "id: taken\nmaterial: STICK\n");
        EditorValidator.Context context = new EditorValidator.Context(
                Set.of("taken"), Map.of(), Set.of(), MECHANICS, tempDir.toFile());
        List<EditorIssue> issues = EditorValidator.validate(doc, CustomItemType.ITEM, context);
        assertEquals("id", errorFor(issues, "id").field());
        assertTrue(errorFor(issues, "id").message().contains("duplicate"));
    }

    @Test
    void fileNameMismatchIsOnlyAWarning() throws IOException {
        EditorDocument doc = document("file_name.yml", "id: different_id\nmaterial: STICK\n");
        List<EditorIssue> issues = validate(doc, CustomItemType.ITEM);
        assertNoErrors(issues);
        assertTrue(hasWarningFor(issues, "id"));
    }

    @Test
    void missingMaterialIsAnError() throws IOException {
        EditorDocument doc = document("none.yml", "display-name: \"&bX\"\n");
        assertTrue(errorFor(validate(doc, CustomItemType.ITEM), "material").message().contains("missing"));
    }

    @Test
    void unknownMaterialIsAnError() throws IOException {
        EditorDocument doc = document("bad.yml", "material: NOT_A_MATERIAL\n");
        assertTrue(errorFor(validate(doc, CustomItemType.ITEM), "material").message().contains("unknown"));
    }

    @Test
    void unknownTypeIsAnError() throws IOException {
        EditorDocument doc = document("bad.yml", "type: WIZARD\nmaterial: STICK\n");
        assertEquals("type", errorFor(validate(doc, CustomItemType.ITEM), "type").field());
    }

    @Test
    void missingTypeIsFineBecauseItDefaultsToItem() throws IOException {
        EditorDocument doc = document("plain.yml", "material: STICK\n");
        assertNoErrors(validate(doc, CustomItemType.ITEM));
    }

    @Test
    void maxStackOutOfRangeIsAnError() throws IOException {
        EditorDocument doc = document("stack.yml", "material: STICK\nmax-stack-size: 500\n");
        assertEquals("max-stack-size", errorFor(validate(doc, CustomItemType.ITEM), "max-stack-size").field());
    }

    @Test
    void nonNumericValueIsAnErrorNamingTheField() throws IOException {
        EditorDocument doc = document("stack.yml", "material: STICK\nmax-stack-size: many\n");
        assertTrue(errorFor(validate(doc, CustomItemType.ITEM), "max-stack-size").message()
                .contains("must be a number"));
    }

    @Test
    void duplicateCustomModelDataIsAnError() throws IOException {
        EditorDocument doc = document("cmd.yml", "material: STICK\ncustom-model-data: 1000\n");
        EditorValidator.Context context = new EditorValidator.Context(
                Set.of(), Map.of(), Set.of(1000), MECHANICS, tempDir.toFile());
        List<EditorIssue> issues = EditorValidator.validate(doc, CustomItemType.ITEM, context);
        assertTrue(errorFor(issues, "custom-model-data").message().contains("already used"));
    }

    @Test
    void blockWithoutBaseBlockIsAnError() throws IOException {
        EditorDocument doc = document("crate.yml", "material: STICK\n");
        assertEquals("base-block", errorFor(validate(doc, CustomItemType.BLOCK), "base-block").field());
    }

    @Test
    void blockBaseUsedByAnotherBlockIsAnError() throws IOException {
        EditorDocument doc = document("crate.yml", "id: crate\nmaterial: STICK\nbase-block: STONE\n");
        EditorValidator.Context context = new EditorValidator.Context(
                Set.of(), Map.of(Material.STONE, "other_crate"), Set.of(), MECHANICS, tempDir.toFile());
        List<EditorIssue> issues = EditorValidator.validate(doc, CustomItemType.BLOCK, context);
        assertTrue(errorFor(issues, "base-block").message().contains("other_crate"));
    }

    @Test
    void denylistedBaseBlockIsAnError() throws IOException {
        EditorDocument doc = document("crate.yml", "id: crate\nmaterial: STICK\nbase-block: TNT\n");
        assertTrue(errorFor(validate(doc, CustomItemType.BLOCK), "base-block").message().contains("not allowed"));
    }

    @Test
    void unknownBaseBlockIsAnError() throws IOException {
        EditorDocument doc = document("crate.yml", "id: crate\nmaterial: STICK\nbase-block: NOT_A_BLOCK\n");
        assertTrue(errorFor(validate(doc, CustomItemType.BLOCK), "base-block").message().contains("unknown"));
    }

    @Test
    void unsupportedBaseShapesAreRejectedByTheLoaderRule() throws IOException {
        // Material.isBlock()/isOccluding() need the live registry, so the name-based half of the
        // rule is asserted here and the shape half is covered by docs/manual-testing.md.
        assertTrue(com.andreaitemmaker.config.ContentLoader.isDeniedBaseName("TNT"));
        assertFalse(com.andreaitemmaker.config.ContentLoader.isDeniedBaseName("STONE"));
        assertFalse(com.andreaitemmaker.config.ContentLoader.isDeniedBaseName(null));
    }

    @Test
    void foodSectionWithoutHungerIsAnError() throws IOException {
        EditorDocument doc = document("pie.yml", """
                type: FOOD
                material: APPLE
                food:
                  saturation: 4
                """);
        assertTrue(ValidatorAssertions.containsError(validate(doc, CustomItemType.FOOD), "food.hunger"));
    }

    @Test
    void foodValuesOutOfRangeAreErrors() throws IOException {
        EditorDocument doc = document("pie.yml", """
                type: FOOD
                material: APPLE
                food:
                  hunger: 50
                  saturation: 4
                  cooldown: 5
                """);
        assertTrue(ValidatorAssertions.containsError(validate(doc, CustomItemType.FOOD), "food.hunger"));
    }

    @Test
    void unknownMechanicIsOnlyAWarning() throws IOException {
        EditorDocument doc = document("sword.yml", """
                material: IRON_SWORD
                mechanics:
                  not_a_mechanic: {}
                """);
        List<EditorIssue> issues = validate(doc, CustomItemType.WEAPON);
        assertNoErrors(issues);
        assertTrue(hasWarningFor(issues, "mechanics.not_a_mechanic"));
    }

    @Test
    void knownMechanicIsNotReported() throws IOException {
        EditorDocument doc = document("sword.yml", """
                material: IRON_SWORD
                mechanics:
                  heal:
                    amount: 3
                """);
        assertTrue(validate(doc, CustomItemType.WEAPON).isEmpty());
    }

    @Test
    void invalidTextureColorIsAnError() throws IOException {
        EditorDocument doc = document("item.yml", "material: STICK\ntexture: \"#zzz\"\n");
        assertEquals("texture", errorFor(validate(doc, CustomItemType.ITEM), "texture").field());
    }

    @Test
    void validHexColorIsAccepted() throws IOException {
        EditorDocument doc = document("item.yml", "material: STICK\ntexture: \"#4f7cff\"\n");
        assertNoErrors(validate(doc, CustomItemType.ITEM));
    }

    @Test
    void unknownTexturePatternIsAnError() throws IOException {
        EditorDocument doc = document("item.yml", """
                material: STICK
                texture:
                  pattern: spiral
                  color: "#4f7cff"
                """);
        assertTrue(ValidatorAssertions.containsError(validate(doc, CustomItemType.ITEM), "texture.pattern"));
    }

    @Test
    void unsafeTexturePathIsAnError() throws IOException {
        EditorDocument doc = document("item.yml", "material: STICK\ntexture: \"../secret.png\"\n");
        assertTrue(errorFor(validate(doc, CustomItemType.ITEM), "texture").message().contains("unsafe"));
    }

    @Test
    void unsafeModelPathIsAnError() throws IOException {
        EditorDocument doc = document("item.yml", "material: STICK\nmodel: \"assets/../../evil.json\"\n");
        assertTrue(errorFor(validate(doc, CustomItemType.ITEM), "model").message().contains("unsafe"));
    }

    @Test
    void nonJsonModelIsAnError() throws IOException {
        EditorDocument doc = document("item.yml", "material: STICK\nmodel: \"assets/models/thing.png\"\n");
        assertTrue(errorFor(validate(doc, CustomItemType.ITEM), "model").message().contains(".json"));
    }

    @Test
    void missingModelFileIsOnlyAWarning() throws IOException {
        EditorDocument doc = document("item.yml", "material: STICK\nmodel: \"assets/models/missing.json\"\n");
        List<EditorIssue> issues = validate(doc, CustomItemType.ITEM);
        assertNoErrors(issues);
        assertTrue(hasWarningFor(issues, "model"));
    }

    @Test
    void unknownAttributeIsAWarningButNonNumericIsAnError() throws IOException {
        EditorDocument doc = document("item.yml", """
                material: STICK
                attributes:
                  attack_damage: fast
                  not_an_attribute: 3
                """);
        List<EditorIssue> issues = validate(doc, CustomItemType.ITEM);
        assertTrue(errorFor(issues, "attributes.attack_damage").message().contains("must be a number"));
        assertTrue(hasWarningFor(issues, "attributes.not_an_attribute"));
    }

    @Test
    void enchantmentLevelMustBeANumber() throws IOException {
        EditorDocument doc = document("item.yml", """
                material: STICK
                enchantments:
                  sharpness: strong
                """);
        assertTrue(errorFor(validate(doc, CustomItemType.ITEM), "enchantments.sharpness").message()
                .contains("must be a number"));
    }

    @Test
    void realEnchantmentNameIsAccepted() throws IOException {
        // The "unknown enchantment" warning needs the live registry (Enchantment.getByKey), so this
        // test asserts only that a sane entry produces no errors.
        EditorDocument doc = document("item.yml", """
                material: STICK
                enchantments:
                  sharpness: 3
                """);
        assertNoErrors(validate(doc, CustomItemType.ITEM));
    }

    @Test
    void furnitureOffsetOutOfRangeIsAnError() throws IOException {
        EditorDocument doc = document("lamp.yml", "material: STICK\noffset-y: 9\n");
        assertTrue(ValidatorAssertions.containsError(validate(doc, CustomItemType.FURNITURE), "offset-y"));
    }

    @Test
    void unknownFurnitureSoundIsAWarning() throws IOException {
        EditorDocument doc = document("lamp.yml", "material: STICK\nplace-sound: not.a.real.sound\n");
        List<EditorIssue> issues = validate(doc, CustomItemType.FURNITURE);
        assertNoErrors(issues);
        assertTrue(hasWarningFor(issues, "place-sound"));
    }

    @Test
    void knownFurnitureSoundIsAccepted() throws IOException {
        EditorDocument doc = document("lamp.yml", """
                material: STICK
                place-sound: block.wood.place
                break-sound: block.wood.break
                """);
        assertTrue(validate(doc, CustomItemType.FURNITURE).isEmpty());
    }

    @Test
    void armorTextureOnANonArmorItemIsAWarning() throws IOException {
        EditorDocument doc = document("item.yml",
                "material: STICK\narmor-texture: \"assets/textures/worn.png\"\n");
        List<EditorIssue> issues = validate(doc, CustomItemType.ITEM);
        assertNoErrors(issues);
        assertTrue(hasWarningFor(issues, "armor-texture"));
    }

    @Test
    void armorWithANonArmorMaterialIsAWarning() throws IOException {
        EditorDocument doc = document("armor.yml", "type: ARMOR\nmaterial: STICK\n");
        List<EditorIssue> issues = validate(doc, CustomItemType.ARMOR);
        assertNoErrors(issues);
        assertTrue(hasWarningFor(issues, "material"));
    }

    @Test
    void inedibleFoodMaterialIsAWarning() throws IOException {
        EditorDocument doc = document("pie.yml", "type: FOOD\nmaterial: STICK\n");
        List<EditorIssue> issues = validate(doc, CustomItemType.FOOD);
        assertNoErrors(issues);
        assertTrue(hasWarningFor(issues, "material"));
    }

    /** Small helpers so the assertions read as intent rather than stream plumbing. */
    static final class ValidatorAssertions {

        static boolean containsError(List<EditorIssue> issues, String field) {
            return issues.stream().anyMatch(issue -> issue.isError() && issue.field().equals(field));
        }
    }
}
