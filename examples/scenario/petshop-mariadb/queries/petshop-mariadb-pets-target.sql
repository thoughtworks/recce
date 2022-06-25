SELECT pet.id AS MigrationKey, category.name, pet.name, status
FROM pet INNER JOIN category ON pet.category_id = category.id
