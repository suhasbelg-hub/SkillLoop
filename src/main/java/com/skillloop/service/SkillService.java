package com.skillloop.service;

import com.skillloop.dao.SkillDAO;
import com.skillloop.exception.AuthorizationException;
import com.skillloop.exception.DuplicateResourceException;
import com.skillloop.exception.NotFoundException;
import com.skillloop.exception.ValidationException;
import com.skillloop.model.Proficiency;
import com.skillloop.model.Skill;
import com.skillloop.model.SkillType;
import com.skillloop.model.StudentSkill;
import com.skillloop.util.DBConnection;
import com.skillloop.util.InputValidator;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/** Skill catalogue management and the "my skills" listings of a student. */
public class SkillService {

    private final SkillDAO skillDAO = new SkillDAO();

    // ------------------------------------------------------------------ catalogue

    public List<Skill> catalogue() {
        return skillDAO.findAllWithDemand();
    }

    public List<Skill> search(String term, String category) {
        return skillDAO.searchSkills(term, category);
    }

    public List<String> categories() {
        return skillDAO.findCategories();
    }

    /**
     * Adds a skill to the shared catalogue. Duplicate names are rejected instead of being
     * silently reused, so the catalogue stays clean.
     */
    public Skill createSkill(String name, String category, String description) {
        Skill skill = new Skill();
        skill.setSkillName(InputValidator.validateSkillName(name));
        skill.setCategory(InputValidator.validateCategory(category));
        skill.setDescription(InputValidator.optionalText(description, "Description", 500));
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Optional<Skill> existing = skillDAO.findSkillByName(connection, skill.getSkillName());
                if (existing.isPresent()) {
                    connection.rollback();
                    throw new DuplicateResourceException("The skill '" + skill.getSkillName() + "' already exists.");
                }
                skill.setSkillId(skillDAO.insertSkill(connection, skill));
                connection.commit();
                return skill;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not create the skill: " + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------ my skills

    /** Adds an OFFER or LEARN listing for the given student. */
    public StudentSkill addListing(long studentId, long skillId, SkillType type, Proficiency proficiency,
                                   String description) {
        if (type == null) {
            throw new ValidationException("Please choose whether you offer or want to learn this skill.");
        }
        if (proficiency == null) {
            throw new ValidationException("Please choose a proficiency level.");
        }
        StudentSkill listing = new StudentSkill();
        listing.setStudentId(studentId);
        listing.setSkillId(skillId);
        listing.setSkillType(type);
        listing.setProficiency(proficiency);
        listing.setDescription(InputValidator.optionalText(description, "Description", 500));
        listing.setActive(true);
        listing.setStudentSkillId(skillDAO.addStudentSkill(listing));
        return listing;
    }

    /** Updates a listing - the service enforces that it belongs to the calling student. */
    public void updateListing(long studentId, long studentSkillId, Proficiency proficiency, String description,
                              boolean active) {
        StudentSkill listing = skillDAO.findStudentSkillById(studentSkillId)
                .orElseThrow(() -> new NotFoundException("Listing #" + studentSkillId + " does not exist."));
        requireOwnership(studentId, listing);
        listing.setProficiency(proficiency == null ? listing.getProficiency() : proficiency);
        listing.setDescription(InputValidator.optionalText(description, "Description", 500));
        listing.setActive(active);
        skillDAO.updateStudentSkill(listing);
    }

    /** Deactivating is the normal way to remove a listing; history and reviews are kept. */
    public void deactivateListing(long studentId, long studentSkillId) {
        StudentSkill listing = skillDAO.findStudentSkillById(studentSkillId)
                .orElseThrow(() -> new NotFoundException("Listing #" + studentSkillId + " does not exist."));
        requireOwnership(studentId, listing);
        listing.setActive(false);
        skillDAO.updateStudentSkill(listing);
    }

    public List<StudentSkill> myListings(long studentId, SkillType type) {
        return skillDAO.listByStudent(studentId, type);
    }

    /** Active teachers of a skill, optionally filtered by a minimum proficiency level. */
    public List<StudentSkill> teachersFor(long skillId, Proficiency minimum) {
        return skillDAO.findTeachers(skillId, minimum);
    }

    private void requireOwnership(long studentId, StudentSkill listing) {
        if (listing.getStudentId() != studentId) {
            throw new AuthorizationException("You can only change your own skill listings.");
        }
    }
}
