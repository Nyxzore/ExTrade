package middleware

import (
	"context"
	"exotrade-server/internal/db"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
)

type MockRow struct {
	ScanFunc func(dest ...any) error
}

func (m *MockRow) Scan(dest ...any) error {
	if m.ScanFunc != nil {
		return m.ScanFunc(dest...)
	}
	return nil
}

type MockPool struct {
	QueryFunc    func(ctx context.Context, sql string, args ...any) (pgx.Rows, error)
	QueryRowFunc func(ctx context.Context, sql string, args ...any) pgx.Row
	ExecFunc     func(ctx context.Context, sql string, args ...any) (pgconn.CommandTag, error)
	BeginFunc    func(ctx context.Context) (pgx.Tx, error)
}

func (m *MockPool) Query(ctx context.Context, sql string, args ...any) (pgx.Rows, error) {
	if m.QueryFunc != nil {
		return m.QueryFunc(ctx, sql, args...)
	}
	return nil, nil
}

func (m *MockPool) QueryRow(ctx context.Context, sql string, args ...any) pgx.Row {
	if m.QueryRowFunc != nil {
		return m.QueryRowFunc(ctx, sql, args...)
	}
	return &MockRow{}
}

func (m *MockPool) Exec(ctx context.Context, sql string, args ...any) (pgconn.CommandTag, error) {
	if m.ExecFunc != nil {
		return m.ExecFunc(ctx, sql, args...)
	}
	return pgconn.CommandTag{}, nil
}

func (m *MockPool) Begin(ctx context.Context) (pgx.Tx, error) {
	if m.BeginFunc != nil {
		return m.BeginFunc(ctx)
	}
	return nil, nil
}

func (m *MockPool) Close() {}

func setupMockDB() {
	db.Pool = &MockPool{}
}
