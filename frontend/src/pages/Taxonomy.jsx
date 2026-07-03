import { useState } from 'react';
import { api } from '../api.js';
import { useLoad } from '../hooks.js';
import Icon from '../components/Icon.jsx';
import Field from '../components/Field.jsx';

export default function Taxonomy({ showToast, canEdit }) {
  const { data: taxonomy, loading, reload } = useLoad(() => api.list('taxonomy'), []);

  // Category Form State
  const [newCategoryName, setNewCategoryName] = useState('');
  const [addingCategory, setAddingCategory] = useState(false);
  const [categoryError, setCategoryError] = useState(null);

  // Skill Form State
  const [newSkillName, setNewSkillName] = useState('');
  const [selectedCategoryId, setSelectedCategoryId] = useState('');
  const [addingSkill, setAddingSkill] = useState(false);
  const [skillError, setSkillError] = useState(null);

  const handleAddCategory = async (e) => {
    e.preventDefault();
    if (!newCategoryName.trim()) return;
    setAddingCategory(true);
    setCategoryError(null);
    try {
      await api.create('taxonomy/categories', { name: newCategoryName.trim() });
      showToast('Skill category created');
      setNewCategoryName('');
      reload();
    } catch (err) {
      setCategoryError(err.message);
    } finally {
      setAddingCategory(false);
    }
  };

  const handleAddSkill = async (e) => {
    e.preventDefault();
    if (!newSkillName.trim() || !selectedCategoryId) return;
    setAddingSkill(true);
    setSkillError(null);
    try {
      await api.create('taxonomy/skills', {
        name: newSkillName.trim(),
        categoryId: Number(selectedCategoryId),
      });
      showToast('Skill registered successfully');
      setNewSkillName('');
      reload();
    } catch (err) {
      setSkillError(err.message);
    } finally {
      setAddingSkill(false);
    }
  };

  const handleDeleteSkill = async (id, name) => {
    if (!window.confirm(`Remove skill "${name}" from taxonomy?`)) return;
    try {
      await api.remove('taxonomy/skills', id);
      showToast('Skill deleted');
      reload();
    } catch (err) {
      showToast(err.message, true);
    }
  };

  const handleDeleteCategory = async (id, name) => {
    if (!window.confirm(`Delete skill category "${name}"?`)) return;
    try {
      await api.remove('taxonomy/categories', id);
      showToast('Category deleted');
      reload();
    } catch (err) {
      showToast(err.message, true);
    }
  };

  if (loading) {
    return (
      <div>
        {[...Array(4)].map((_, i) => (
          <div key={i} className="skeleton-row" />
        ))}
      </div>
    );
  }

  return (
    <div style={{ display: 'grid', gridTemplateColumns: '3fr 2fr', gap: '24px' }} className="form-grid">
      {/* Taxonomy list (left side) */}
      <div style={{ display: 'grid', gap: '18px', alignContent: 'start' }}>
        {(!taxonomy || taxonomy.length === 0) ? (
          <div className="card">
            <div className="empty-state">
              <Icon name="inbox" size={40} />
              <p>No skill taxonomy defined yet.</p>
            </div>
          </div>
        ) : (
          taxonomy.map((cat) => (
            <div key={cat.id} className="card" style={{ padding: '20px', animation: 'fade-in 0.25s ease' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '14px' }}>
                <h3 style={{ margin: 0, fontSize: '15px', fontWeight: '700', textTransform: 'uppercase', letterSpacing: '0.03em' }}>
                  {cat.name}
                </h3>
                {canEdit && (!cat.skills || cat.skills.length === 0) && (
                  <button
                    className="btn btn-danger btn-sm"
                    onClick={() => handleDeleteCategory(cat.id, cat.name)}
                    style={{ padding: '4px 8px', fontSize: '12px' }}
                    title="Delete empty category"
                  >
                    Delete Category
                  </button>
                )}
              </div>
              {(!cat.skills || cat.skills.length === 0) ? (
                <p className="cell-sub" style={{ fontSize: '13px', margin: 0 }}>No skills defined in this category.</p>
              ) : (
                <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
                  {cat.skills.map((skill) => (
                    <span
                      key={skill.id}
                      className="badge badge-gray"
                      style={{
                        display: 'inline-flex',
                        alignItems: 'center',
                        gap: '6px',
                        padding: '4px 10px',
                        fontSize: '13px',
                      }}
                    >
                      {skill.name}
                      {canEdit && (
                        <button
                          type="button"
                          onClick={() => handleDeleteSkill(skill.id, skill.name)}
                          style={{
                            background: 'none',
                            border: 'none',
                            cursor: 'pointer',
                            color: 'var(--color-muted-fg)',
                            padding: '0 2px',
                            display: 'flex',
                            alignItems: 'center',
                            fontSize: '14px',
                            fontWeight: 'bold',
                          }}
                          onMouseEnter={(e) => (e.target.style.color = 'var(--color-destructive)')}
                          onMouseLeave={(e) => (e.target.style.color = 'var(--color-muted-fg)')}
                          title="Remove skill"
                        >
                          &times;
                        </button>
                      )}
                    </span>
                  ))}
                </div>
              )}
            </div>
          ))
        )}
      </div>

      {/* Forms (right side) */}
      <div style={{ display: 'grid', gap: '20px', alignContent: 'start' }}>
        {canEdit && (
          <>
            {/* Add Category Form */}
            <div className="card" style={{ padding: '24px' }}>
              <h3 style={{ margin: '0 0 16px 0' }}>Add Skill Category</h3>
              {categoryError && <div className="form-alert">{categoryError}</div>}
              <form onSubmit={handleAddCategory}>
                <Field label="Category Name" required>
                  <input
                    value={newCategoryName}
                    onChange={(e) => setNewCategoryName(e.target.value)}
                    placeholder="e.g. Cloud Platforms"
                    disabled={addingCategory}
                    required
                  />
                </Field>
                <button
                  type="submit"
                  className="btn btn-primary"
                  style={{ width: '100%', marginTop: '8px' }}
                  disabled={addingCategory || !newCategoryName.trim()}
                >
                  {addingCategory ? 'Adding…' : 'Add Category'}
                </button>
              </form>
            </div>

            {/* Add Skill Form */}
            <div className="card" style={{ padding: '24px' }}>
              <h3 style={{ margin: '0 0 16px 0' }}>Add Skill / Tool</h3>
              {skillError && <div className="form-alert">{skillError}</div>}
              <form onSubmit={handleAddSkill}>
                <Field label="Category" required>
                  <select
                    value={selectedCategoryId}
                    onChange={(e) => setSelectedCategoryId(e.target.value)}
                    disabled={addingSkill}
                    required
                  >
                    <option value="">Select category…</option>
                    {(taxonomy || []).map((c) => (
                      <option key={c.id} value={c.id}>
                        {c.name}
                      </option>
                    ))}
                  </select>
                </Field>
                <Field label="Skill Name" required>
                  <input
                    value={newSkillName}
                    onChange={(e) => setNewSkillName(e.target.value)}
                    placeholder="e.g. AWS"
                    disabled={addingSkill}
                    required
                  />
                </Field>
                <button
                  type="submit"
                  className="btn btn-primary"
                  style={{ width: '100%', marginTop: '8px' }}
                  disabled={addingSkill || !newSkillName.trim() || !selectedCategoryId}
                >
                  {addingSkill ? 'Registering…' : 'Add Skill'}
                </button>
              </form>
            </div>
          </>
        )}
      </div>
    </div>
  );
}
