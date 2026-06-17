/*
 * Copyright 2002-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.samples.petclinic.customers.web;

import io.micrometer.core.annotation.Timed;
import jakarta.validation.constraints.Min;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.samples.petclinic.customers.model.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * @author Juergen Hoeller
 * @author Ken Krebs
 * @author Arjen Poutsma
 * @author Maciej Szarlinski
 * @author Ramazan Sakin
 */
@RestController
@Timed("petclinic.pet")
class PetResource {

    private static final Logger log = LoggerFactory.getLogger(PetResource.class);

    private final PetRepository petRepository;
    private final OwnerRepository ownerRepository;

    PetResource(PetRepository petRepository, OwnerRepository ownerRepository) {
        this.petRepository = petRepository;
        this.ownerRepository = ownerRepository;
    }

    @GetMapping("/petTypes")
    public List<PetType> getPetTypes() {
        return petRepository.findPetTypes();
    }

    @PostMapping("/owners/{ownerId}/pets")
    @ResponseStatus(HttpStatus.CREATED)
    public Pet processCreationForm(
        @RequestBody PetRequest petRequest,
        @PathVariable("ownerId") @Min(1) int ownerId) {

        Owner owner = ownerRepository.findById(ownerId)
            .orElseThrow(() -> new ResourceNotFoundException("Owner " + ownerId + " not found"));

        if(owner.hasPetNamed(petRequest.name())){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pet with name " + petRequest.name() + " already exists");
        } else if (owner.getPets().size() >= 5){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Too many pets");
        }

        //since petType is optional
        PetType petType = petRepository.findPetTypeById(petRequest.typeId()).orElseThrow(() -> new ResourceNotFoundException("PetType " + petRequest.typeId() + " not found"));

        Pet pet = new Pet();
        pet.setType(petType);
        applyNamingPolicy(pet, petRequest, petType);
        owner.addPet(pet);
        return petRepository.save(pet);
    }

    @PutMapping("/owners/*/pets/{petId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void processUpdateForm(@RequestBody PetRequest petRequest) {
        int petId = petRequest.id();
        Pet pet = findPetById(petId);
        save(pet, petRequest);
    }

    private Pet save(final Pet pet, final PetRequest petRequest) {

        pet.setName(petRequest.name());
        pet.setBirthDate(petRequest.birthDate());

        petRepository.findPetTypeById(petRequest.typeId())
            .ifPresent(pet::setType);

        log.info("Saving pet {}", pet);
        return petRepository.save(pet);
    }

    @GetMapping("owners/*/pets/{petId}")
    public PetDetails findPet(@PathVariable("petId") int petId) {
        Pet pet = findPetById(petId);
        return new PetDetails(pet);
    }


    private Pet findPetById(int petId) {
        return petRepository.findById(petId)
            .orElseThrow(() -> new ResourceNotFoundException("Pet " + petId + " not found"));
    }

    void applyNamingPolicy(Pet pet, PetRequest petRequest, PetType petType) {
        String baseName = petRequest.name().trim();

        switch (petType.getName().toLowerCase()) {               // switch statement
            case "cat":
            case "dog":
                pet.setName(baseName + " " + pet.fetchOwnerLastName());
                break;
            case "snake":
            case "lizard":
                pet.setName(pet.fetchOwnerFirstName() + "'s " + baseName);
                break;
            case "bird":
            case "hamster":
                pet.setName("small " + baseName);
                break;
            default:
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unsupported pet type: " + petType.getName());
        }

        pet.setBirthDate(petRequest.birthDate());
    }

}
