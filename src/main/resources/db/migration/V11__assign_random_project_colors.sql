WITH project_color_palette AS (
    SELECT ARRAY[
        '#ef4444',
        '#f97316',
        '#f59e0b',
        '#eab308',
        '#84cc16',
        '#22c55e',
        '#10b981',
        '#14b8a6',
        '#06b6d4',
        '#0ea5e9',
        '#3b82f6',
        '#6366f1',
        '#8b5cf6',
        '#a855f7',
        '#d946ef',
        '#ec4899',
        '#f43f5e'
    ]::VARCHAR[] AS colors
)
UPDATE projects
SET color = colors[FLOOR(RANDOM() * CARDINALITY(colors))::INTEGER + 1]
FROM project_color_palette
WHERE projects.color IS NULL
   OR projects.color = '#0b83d9';
