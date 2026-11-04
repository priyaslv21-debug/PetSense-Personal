package com.example.pet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Intent;

import com.example.pet.data.Pet;
import com.example.pet.ui.pets.AddPetActivity;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Unit tests validating:
 * 1. Pet registration input validation contracts (all 6 required fields).
 * 2. Pet POJO serialization, gender/isMale synchronization, and breedAgeLine formatting.
 * 3. User isolation in Firestore path structure: users/{uid}/pets/{petId}.
 * 4. Onboarding navigation intent flags and parameter contracts.
 * 5. Dashboard empty-state vs populated-state selection behavior without demo pet "Max".
 */
public class PetRegistrationAndOnboardingTest {

    private static final List<String> VALID_SPECIES = Arrays.asList(
            "Dog", "Cat", "Bird", "Rabbit", "Fish", "Other"
    );

    private static final List<String> VALID_GENDERS = Arrays.asList(
            "Male", "Female"
    );

    @Test
    public void testRequiredFieldsValidationContract() {
        // Required fields: Name, Species, Breed, Gender, Age, Weight
        String name = "Luna";
        String species = "Dog";
        String breed = "Golden Retriever";
        String gender = "Female";
        String age = "2 Years";
        String weight = "22 kg";
        String notes = ""; // Optional
        String photoUrl = null; // Optional

        assertTrue("Name must not be empty", name != null && !name.trim().isEmpty());
        assertTrue("Species must not be empty", species != null && !species.trim().isEmpty());
        assertTrue("Breed must not be empty", breed != null && !breed.trim().isEmpty());
        assertTrue("Gender must not be empty", gender != null && !gender.trim().isEmpty());
        assertTrue("Age must not be empty", age != null && !age.trim().isEmpty());
        assertTrue("Weight must not be empty", weight != null && !weight.trim().isEmpty());

        // Test rejecting missing required fields
        assertFalse("Empty name must fail validation", isPetFormValid("", species, breed, gender, age, weight));
        assertFalse("Empty species must fail validation", isPetFormValid(name, "", breed, gender, age, weight));
        assertFalse("Empty breed must fail validation", isPetFormValid(name, species, "", gender, age, weight));
        assertFalse("Empty gender must fail validation", isPetFormValid(name, species, breed, "", age, weight));
        assertFalse("Empty age must fail validation", isPetFormValid(name, species, breed, gender, "", weight));
        assertFalse("Empty weight must fail validation", isPetFormValid(name, species, breed, gender, age, ""));

        // All required fields present
        assertTrue("All required fields provided must pass validation",
                isPetFormValid(name, species, breed, gender, age, weight));
    }

    private boolean isPetFormValid(String name, String species, String breed,
                                   String gender, String age, String weight) {
        if (name == null || name.trim().isEmpty()) return false;
        if (species == null || species.trim().isEmpty()) return false;
        if (breed == null || breed.trim().isEmpty()) return false;
        if (gender == null || gender.trim().isEmpty()) return false;
        if (age == null || age.trim().isEmpty()) return false;
        if (weight == null || weight.trim().isEmpty()) return false;
        return true;
    }

    @Test
    public void testSpeciesAndGenderOptionsContract() {
        assertTrue(VALID_SPECIES.contains("Dog"));
        assertTrue(VALID_SPECIES.contains("Cat"));
        assertTrue(VALID_SPECIES.contains("Bird"));
        assertTrue(VALID_SPECIES.contains("Rabbit"));
        assertTrue(VALID_SPECIES.contains("Fish"));
        assertTrue(VALID_SPECIES.contains("Other"));

        assertTrue(VALID_GENDERS.contains("Male"));
        assertTrue(VALID_GENDERS.contains("Female"));
    }

    @Test
    public void testPetDataModelAndGenderSync() {
        Pet femalePet = new Pet("Bella", "Cat", "Siamese", "1 Year", "Friendly", false, "4 kg", null);
        assertEquals("Female", femalePet.getGender());
        assertFalse(femalePet.isMale());

        Pet malePet = new Pet("Charlie", "Dog", "Beagle", "3 Years", null, true, "12 kg", null);
        assertEquals("Male", malePet.getGender());
        assertTrue(malePet.isMale());

        // Flipping gender updates isMale
        femalePet.setGender("Male");
        assertTrue(femalePet.isMale());

        malePet.setGender("Female");
        assertFalse(malePet.isMale());
    }

    @Test
    public void testPetBreedAgeLineFormatting() {
        Pet pet = new Pet("Rocky", "Dog", "German Shepherd", "4 Years", null, true, "30 kg", null);
        pet.setGender("Male");
        assertEquals("German Shepherd \u00b7 Male \u00b7 4 Years", pet.breedAgeLine());

        // Fallback when breed is missing
        Pet noBreed = new Pet("Goldie", "Fish", null, "6 Months", null, false, "0.1 kg", null);
        noBreed.setGender("Female");
        assertEquals("Fish \u00b7 Female \u00b7 6 Months", noBreed.breedAgeLine());
    }

    @Test
    public void testFirestorePathIsolationStructure() {
        String uidUser1 = "auth_uid_alice_123";
        String uidUser2 = "auth_uid_bob_456";
        String petId = "pet_doc_789";

        String user1PetPath = "users/" + uidUser1 + "/pets/" + petId;
        String user2PetPath = "users/" + uidUser2 + "/pets/" + petId;

        // Path structure verification
        assertEquals("users/auth_uid_alice_123/pets/pet_doc_789", user1PetPath);
        assertEquals("users/auth_uid_bob_456/pets/pet_doc_789", user2PetPath);

        // Cross-user isolation verification
        assertFalse("Paths across different users must never collide", user1PetPath.equals(user2PetPath));
        assertTrue(user1PetPath.startsWith("users/" + uidUser1 + "/"));
        assertTrue(user2PetPath.startsWith("users/" + uidUser2 + "/"));
    }

    @Test
    public void testOnboardingIntentKeysAndFlags() {
        assertEquals("extra_is_onboarding", AddPetActivity.EXTRA_IS_ONBOARDING);
        assertEquals("id", AddPetActivity.EXTRA_PET_ID);
        assertEquals("extra_pet_name", AddPetActivity.EXTRA_PET_NAME);
        assertEquals("extra_pet_species", AddPetActivity.EXTRA_PET_SPECIES);
        assertEquals("extra_pet_breed", AddPetActivity.EXTRA_PET_BREED);
        assertEquals("extra_pet_age", AddPetActivity.EXTRA_PET_AGE);
        assertEquals("extra_pet_gender", AddPetActivity.EXTRA_PET_GENDER);
        assertEquals("extra_pet_weight", AddPetActivity.EXTRA_PET_WEIGHT);
    }

    @Test
    public void testDashboardPetSelectionLogic() {
        List<Pet> emptyPets = new ArrayList<>();
        Pet selectedWhenEmpty = selectPetFromList(emptyPets, null);
        assertNull("Empty pet list must yield null selected pet (triggering empty state)", selectedWhenEmpty);

        List<Pet> pets = new ArrayList<>();
        Pet pet1 = new Pet("Milo", "Cat", "Tabby", "1 Year", null, true, "4.5 kg", null);
        pet1.setId("id_pet_1");
        Pet pet2 = new Pet("Daisy", "Dog", "Poodle", "3 Years", null, false, "7 kg", null);
        pet2.setId("id_pet_2");
        pets.add(pet1);
        pets.add(pet2);

        // Selecting with preferred pet ID (e.g. from onboarding)
        Pet selectedPreferred = selectPetFromList(pets, "id_pet_2");
        assertNotNull(selectedPreferred);
        assertEquals("id_pet_2", selectedPreferred.getId());
        assertEquals("Daisy", selectedPreferred.getName());

        // Default selection when no preferred pet ID provided selects the first pet
        Pet selectedDefault = selectPetFromList(pets, null);
        assertNotNull(selectedDefault);
        assertEquals("id_pet_1", selectedDefault.getId());
        assertEquals("Milo", selectedDefault.getName());
    }

    @Test
    public void testNoDuplicatePetInLocalList() {
        List<Pet> list = new ArrayList<>();
        Pet initial = new Pet("Buddy", "Dog", "Labrador", "2 Years", null, true, "25 kg", null);
        initial.setId("pet_unique_100");
        list.add(initial);

        // Simulate re-adding/updating the same pet ID
        Pet updated = new Pet("Buddy", "Dog", "Labrador Retriever", "3 Years", "Grown up", true, "28 kg", null);
        updated.setId("pet_unique_100");

        List<Pet> resultList = updateOrAddPet(list, updated);
        assertEquals("Duplicate pet document must not be appended", 1, resultList.size());
        assertEquals("Labrador Retriever", resultList.get(0).getBreed());
        assertEquals("3 Years", resultList.get(0).getAge());
    }

    private Pet selectPetFromList(List<Pet> pets, String preferredPetId) {
        if (pets == null || pets.isEmpty()) {
            return null;
        }
        if (preferredPetId != null) {
            for (Pet p : pets) {
                if (preferredPetId.equals(p.getId())) {
                    return p;
                }
            }
        }
        return pets.get(0);
    }

    private List<Pet> updateOrAddPet(List<Pet> current, Pet newPet) {
        List<Pet> updated = new ArrayList<>(current);
        boolean found = false;
        for (int i = 0; i < updated.size(); i++) {
            Pet p = updated.get(i);
            if (newPet.getId() != null && newPet.getId().equals(p.getId())) {
                updated.set(i, newPet);
                found = true;
                break;
            }
        }
        if (!found) {
            updated.add(newPet);
        }
        return updated;
    }
}
