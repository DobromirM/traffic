// Copyright 2015-2019 SWIM.AI inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package swim.traffic.agent;

import swim.api.SwimLane;
import swim.api.SwimResident;
import swim.api.agent.AbstractAgent;
import swim.api.downlink.MapDownlink;
import swim.api.lane.JoinValueLane;
import swim.api.lane.SpatialLane;
import swim.collections.HashTrieMap;
import swim.math.CircleR2;
import swim.math.PointR2;
import swim.math.R2Shape;
import swim.spatial.SpatialMap;
import swim.structure.Form;
import swim.structure.Value;
import swim.uri.Uri;

import java.util.Iterator;

public class CityAgent extends AbstractAgent {

  @SwimLane("city/map")
  public SpatialLane<Uri, R2Shape, Value> cityMap = geospatialLane()
      .keyForm(Uri.form())
      .valueForm(Form.forValue());

  MapDownlink<Uri, Value> intersectionsLink;

  @SwimLane("intersections")
  @SwimResident
  public JoinValueLane<Uri, Value> intersections = joinValueLane()
      .keyForm(Uri.form())
      .valueForm(Form.forValue())
      .didUpdate((key, newValue, oldValue) -> computeNeighbors(key, newValue, oldValue));

  void computeNeighbors(Uri intersectionUri, Value newInfo, Value oldInfo) {
    if (newInfo.get("lng").isDefined() && newInfo.get("lat").isDefined()) {
      final double newLng = newInfo.get("lng").doubleValue();
      final double newLat = newInfo.get("lat").doubleValue();
      // Add to the spatial lane by creating an R2Point with the given lat/lng
      cityMap.put(intersectionUri, new PointR2(newLng, newLat), newInfo);

      // Get the snapshot from the spatial lane
      final SpatialMap<Uri, R2Shape, Value> cityMapSnapshot = cityMap.snapshot();

      HashTrieMap<Uri, Value> newIntersections = HashTrieMap.empty();
      if (!Double.isNaN(newLng) && !Double.isNaN(newLat)) {
        // create an R2Shape- a Circle in the case, with the lat/lng as the center and 0.0025 as radius (0.0025 equates ~.25-.27 km)
        final CircleR2 newCircle = new CircleR2(newLng, newLat, 0.0025);

        // get spatial lane's snapshot's iterator based on the R2Shape, this returns all the points in the spatial lane
        // within the circle
        final Iterator<SpatialLane.Entry<Uri, R2Shape, Value>> newProximity = cityMapSnapshot.iterator(newCircle);
        while (newProximity.hasNext()) {
          final SpatialLane.Entry<Uri, R2Shape, Value> entry = newProximity.next();
          newIntersections = newIntersections.updated(entry.getKey(), entry.getValue());
          final Uri neighborUri = entry.getKey();
          if (!neighborUri.equals(intersectionUri)) {
            System.out.println(intersectionUri + " found intersection within approximately 0.25km radius-" + entry.getKey());
          }
        }
      }
    }
  }

  public void linkIntersections() {
    if (intersectionsLink == null) {
      intersectionsLink = downlinkMap()
          .keyForm(Uri.form())
          .hostUri(TRAFFIC_HOST)
          .nodeUri(Uri.from(nodeUri().path()))
          .laneUri("intersections")
          .didUpdate(this::didUpdateRemoteIntersection)
          .open();
    }
  }

  public void unlinkIntersections() {
    if (intersectionsLink != null) {
      intersectionsLink.close();
      intersectionsLink = null;
    }
  }

  void didUpdateRemoteIntersection(Uri intersectionUri, Value newValue, Value oldValue) {
    //System.out.println(nodeUri() + " didUpdateRemoteIntersection: " + intersectionUri);
    if (!intersections.containsKey(intersectionUri)) {
      intersections.downlink(intersectionUri)
          .nodeUri(intersectionUri)
          .laneUri(INTERSECTION_INFO)
          .open();
    }
  }

  public void didStart() {
    System.out.println(nodeUri() + " didStart");
    linkIntersections();
  }

  public void willStop() {
    unlinkIntersections();
  }

  static final Uri TRAFFIC_HOST = Uri.parse("warps://trafficware.swim.services?key=ab21cfe05ba-7d43-69b2-0aef-94d9d54b6f65");
  static final Uri INTERSECTION_INFO = Uri.parse("intersection/info");
  static final Uri NEIGHBOR_ADD = Uri.parse("neighbor/add");
  static final Uri NEIGHBOR_REMOVE = Uri.parse("neighbor/remove");
}
