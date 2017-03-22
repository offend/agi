// Copyright (C) 2017 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package resolve

import (
	"context"

	"github.com/google/gapid/gapis/capture"
	"github.com/google/gapid/gapis/database"
	"github.com/google/gapid/gapis/service/path"
)

// Get resolves the object, value or memory at p.
func Get(ctx context.Context, p *path.Any) (interface{}, error) {
	return database.Build(ctx, &GetResolvable{p})
}

// Resolve implements the database.Resolver interface.
func (r *GetResolvable) Resolve(ctx context.Context) (interface{}, error) {
	if c := path.FindCapture(r.Path.Node()); c != nil {
		ctx = capture.Put(ctx, c)
	}
	return Resolve(ctx, r.Path.Node())
}
