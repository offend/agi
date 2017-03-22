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

package atom_pb

import (
	"context"

	"github.com/golang/protobuf/proto"
)

// Atom is the interface implemented by all atom storage objects.
type Atom interface {
	proto.Message
}

// Handler is a function to which a stream of atoms can be handed.
type Handler func(context.Context, Atom) error

// InvokeMarker is the singleton instance of Invoke that can be used to avoid allocations.
var InvokeMarker Atom = &Invoke{}
