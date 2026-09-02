package top.ortus.lightmark.user.service;

import org.springframework.stereotype.Service;
import top.ortus.lightmark.common.exception.ApiException;
import top.ortus.lightmark.user.dao.TravelerRepository;
import top.ortus.lightmark.user.dto.module.TravelerDTO;

import java.util.List;

@Service
public class TravelerService {
    private final TravelerRepository repository;

    public TravelerService(TravelerRepository repository) {
        this.repository = repository;
    }

    public List<TravelerDTO> list(String userId) {
        requireUser(userId);
        return repository.findByUserId(userId);
    }

    public TravelerDTO create(String userId, TravelerDTO request) {
        requireUser(userId);
        validate(request);
        request.setUser_id(userId);
        repository.insert(request);
        List<TravelerDTO> items = repository.findByUserId(userId);
        return items.isEmpty() ? request : items.get(0);
    }

    public TravelerDTO update(String userId, String id, TravelerDTO request) {
        requireUser(userId);
        if (repository.findOwned(id, userId) == null) throw new ApiException(404, "traveler not found");
        validate(request);
        request.setId(id);
        request.setUser_id(userId);
        repository.update(request);
        return repository.findOwned(id, userId);
    }

    public boolean delete(String userId, String id) {
        requireUser(userId);
        if (repository.findOwned(id, userId) == null) throw new ApiException(404, "traveler not found");
        return repository.delete(id, userId) > 0;
    }

    private void validate(TravelerDTO request) {
        if (request == null || request.getName() == null || request.getName().isBlank()
                || request.getId_card() == null || request.getId_card().isBlank()) {
            throw new ApiException(400, "traveler name and idCard are required");
        }
    }

    private void requireUser(String userId) {
        if (userId == null || userId.isBlank() || "0".equals(userId)) throw new ApiException(401, "unauthorized");
    }
}
