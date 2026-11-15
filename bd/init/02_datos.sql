SET NAMES utf8mb4;

-- Datos semilla. Las materias quedan con los ids 1 a 6 en este orden.

INSERT INTO materias (id, codigo, nombre) VALUES
    (1, 'MAT1', 'Cálculo Diferencial'),
    (2, 'IS3',  'Ingeniería de Software III'),
    (3, 'BD1',  'Bases de Datos'),
    (4, 'POO',  'Programación Orientada a Objetos'),
    (5, 'EDA',  'Estructuras de Datos'),
    (6, 'RED',  'Redes de Computadores');

-- Dos monitores de ejemplo. Sus códigos no chocan con los de los ejemplos
-- de la documentación (M001), para poder probar el registro sin borrar nada.
INSERT INTO monitores (codigo, nombre_completo, correo, materia_id) VALUES
    ('M100', 'Laura Sofía Ramírez Ospina', 'laura.ramirez@ucaldas.edu.co', 2),
    ('M101', 'Andrés Felipe Muñoz Castaño', 'andres.munoz@ucaldas.edu.co', 3);
