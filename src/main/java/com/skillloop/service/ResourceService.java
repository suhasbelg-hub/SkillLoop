package com.skillloop.service;

import com.skillloop.dao.ResourceDAO;
import com.skillloop.exception.AuthorizationException;
import com.skillloop.exception.NotFoundException;
import com.skillloop.exception.ValidationException;
import com.skillloop.model.LearningResource;
import com.skillloop.util.InputValidator;

import java.util.List;

/** Sharing of notes, links and practice material. */
public class ResourceService {

    private final ResourceDAO resourceDAO = new ResourceDAO();

    public List<LearningResource> all(int limit) {
        return resourceDAO.listAll(limit);
    }

    public List<LearningResource> forSkill(long skillId) {
        return resourceDAO.listForSkill(skillId);
    }

    public List<LearningResource> mine(long studentId) {
        return resourceDAO.listByStudent(studentId);
    }

    /** Shares a resource; a title is required, the URL is optional (notes may be offline). */
    public LearningResource share(long studentId, long skillId, String title, String description, String url) {
        if (skillId <= 0) {
            throw new ValidationException("Please choose the skill this resource belongs to.");
        }
        LearningResource resource = new LearningResource();
        resource.setStudentId(studentId);
        resource.setSkillId(skillId);
        resource.setTitle(InputValidator.requireText(title, "Title", 150));
        resource.setDescription(InputValidator.optionalText(description, "Description", 500));
        resource.setResourceUrl(InputValidator.validateUrl(url));
        resource.setResourceId(resourceDAO.insert(resource));
        return resource;
    }

    /** Deletes a resource - only the author may do so. */
    public void delete(long studentId, long resourceId) {
        LearningResource resource = resourceDAO.listByStudent(studentId).stream()
                .filter(r -> r.getResourceId() == resourceId)
                .findFirst()
                .orElseThrow(() -> new NotFoundException(
                        "Resource #" + resourceId + " does not exist or is not yours."));
        if (resource.getStudentId() != studentId) {
            throw new AuthorizationException("You can only delete resources you shared yourself.");
        }
        resourceDAO.delete(resourceId, studentId);
    }
}
