package db

import (
	"context"
	"fmt"
	"strings"
)

// LogImpressions batch-inserts listing/breeding impression rows for the hotness algorithm.
func LogImpressions(ctx context.Context, userID string, listingIDs []int64, table string) error {
	if len(listingIDs) == 0 {
		return nil
	}
	if table != "listing_impressions" && table != "breeding_impressions" {
		return fmt.Errorf("invalid impressions table: %s", table)
	}

	placeholders := make([]string, 0, len(listingIDs))
	params := make([]any, 0, len(listingIDs)*2)
	i := 1
	for _, id := range listingIDs {
		placeholders = append(placeholders, fmt.Sprintf("($%d::integer, $%d::uuid)", i, i+1))
		params = append(params, id, userID)
		i += 2
	}

	query := fmt.Sprintf(`
		INSERT INTO %s (listing_id, user_id)
		SELECT data.listing_id, data.user_id
		FROM (VALUES %s) AS data(listing_id, user_id)`,
		table, strings.Join(placeholders, ", "))

	_, err := Pool.Exec(ctx, query, params...)
	return err
}
