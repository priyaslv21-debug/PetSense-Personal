package com.example.pet.data;

import androidx.annotation.Keep;

/**
 * A single pet profile. Plain POJO on purpose — Firestore needs a public
 * no-arg constructor and public getters (or matching field names) to
 * deserialize documents automatically via DocumentSnapshot.toObject(Pet.class).
 */
@Keep
public class Pet {

    private String id;          // Firestore document id — set after save, not before
    private String name;
    private String species;     // one of ps_species_options: Dog, Cat, Bird, Rabbit, Fish, Other
    private String breed;       // optional
    private String age;         // free-text, e.g. "2 years" or "6 months" — kept simple on purpose
    private String notes;       // optional
    private boolean isMale;     // legacy boolean — kept for backward compat; prefer gender
    private String gender;      // "Male" | "Female" | null (null = unknown / old Firestore doc)
    private String weight;      // e.g. "24 kg"
    private String photoUrl;    // local URI or remote URL for the pet's photo

    /** Required no-arg constructor for Firestore deserialization. */
    public Pet() {
    }

    public Pet(String name, String species, String breed, String age, String notes, boolean isMale, String weight, String photoUrl) {
        this.name = name;
        this.species = species;
        this.breed = breed;
        this.age = age;
        this.notes = notes;
        this.isMale = isMale;
        this.gender = isMale ? "Male" : "Female"; // derive initial value from legacy flag
        this.weight = weight;
        this.photoUrl = photoUrl;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSpecies() { return species; }
    public void setSpecies(String species) { this.species = species; }

    public String getBreed() { return breed; }
    public void setBreed(String breed) { this.breed = breed; }

    public String getAge() { return age; }
    public void setAge(String age) { this.age = age; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public boolean isMale() { return isMale; }
    public void setMale(boolean male) {
        isMale = male;
        // keep gender in sync when the legacy flag is flipped
        if (gender == null) gender = male ? "Male" : "Female";
    }

    public String getGender() { return gender; }
    public void setGender(String gender) {
        this.gender = gender;
        // keep legacy flag in sync
        if (gender != null) isMale = !"Female".equalsIgnoreCase(gender);
    }

    public String getWeight() { return weight; }
    public void setWeight(String weight) { this.weight = weight; }

    public String getPhotoUrl() { return photoUrl; }
    public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }

    /**
     * "Golden Retriever · Male · 2 Years" style line for summary cards.
     * Any null/empty segment is skipped gracefully so old Firestore docs
     * (which have no gender field) still render correctly.
     */
    public String breedAgeLine() {
        String left = (breed != null && !breed.trim().isEmpty()) ? breed.trim() : species;
        String g = (gender != null && !gender.trim().isEmpty()) ? gender.trim() : null;
        String a = (age != null && !age.trim().isEmpty()) ? age.trim() : null;

        StringBuilder sb = new StringBuilder(left != null ? left : "");
        if (g != null) sb.append(" \u00b7 ").append(g);
        if (a != null) sb.append(" \u00b7 ").append(a);
        return sb.toString();
    }
}
