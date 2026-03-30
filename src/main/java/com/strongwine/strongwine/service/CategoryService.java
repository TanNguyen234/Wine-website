package com.strongwine.strongwine.service;

import com.strongwine.strongwine.entity.Category;
import com.strongwine.strongwine.repository.CategoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class CategoryService {

    @Autowired
    private CategoryRepository categoryRepository;

    public List<Category> getAllCategories() {
        return categoryRepository.findAll().stream().filter(c -> !c.isDeleted()).toList();
    }

    public Category getCategoryByIdOrThrow(Long id) {
        return categoryRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new RuntimeException("Category not found with id: " + id));
    }

    public Category createCategory(Category category) {
        if (categoryRepository.existsByNameAndDeletedFalse(category.getName())) {
            throw new IllegalArgumentException("Category name already exists");
        }
        return categoryRepository.save(category);
    }

    public Category updateCategory(Long id, Category details) {
        Category category = getCategoryByIdOrThrow(id);
        if (!category.getName().equals(details.getName()) && categoryRepository.existsByNameAndDeletedFalse(details.getName())) {
            throw new IllegalArgumentException("Category name already exists");
        }
        category.setName(details.getName());
        category.setDescription(details.getDescription());
        return categoryRepository.save(category);
    }

    public void deleteCategory(Long id) {
        Category category = getCategoryByIdOrThrow(id);
        category.setDeleted(true);
        categoryRepository.save(category);
    }
}
